package brentmaas.buildguide.fabric.shape;

import java.util.List;
import java.util.Optional;
import java.util.OptionalDouble;

import org.joml.Matrix4f;
import org.joml.Matrix4fc;
import org.joml.Vector3f;
import org.joml.Vector4f;

import com.mojang.renderpearl.api.pipeline.PrimitiveTopology;
import com.mojang.renderpearl.api.buffers.GpuBuffer;
import com.mojang.renderpearl.api.buffers.GpuBufferSlice;
import com.mojang.renderpearl.api.pipeline.RenderPipeline;
import com.mojang.blaze3d.pipeline.RenderTarget;
import com.mojang.renderpearl.api.commands.RenderPass;
import com.mojang.blaze3d.systems.RenderSystem;
import com.mojang.renderpearl.api.textures.GpuTextureView;
import com.mojang.blaze3d.vertex.BufferBuilder;
import com.mojang.blaze3d.vertex.ByteBufferBuilder;
import com.mojang.blaze3d.vertex.DefaultVertexFormat;
import com.mojang.blaze3d.vertex.MeshData;

import brentmaas.buildguide.common.shape.GpuMemory;
import brentmaas.buildguide.common.shape.IShapeBuffer;
import brentmaas.buildguide.common.shape.VertexMemory;
import brentmaas.buildguide.fabric.RenderHandler;
import net.minecraft.client.Minecraft;

/**
 * One mesh: the vertices are written into a native builder (on the generation thread for a shape, on the
 * render thread for the overlay, the Area 3 outline and the preview), sent to the GPU by end() and drawn
 * from there. The native copy is freed exactly once: by end() right after the upload, or, for a shape
 * generation that never reaches the world, by Shape under its lock (releaseVertexData). close() frees the
 * GPU buffer only.
 */
public class ShapeBuffer implements IShapeBuffer {
	// POSITION_COLOR: 3 floats and 4 colour bytes, what the builder reserves per vertex
	private static final int vertexBytes = 16, initialBytes = 28;
	private ByteBufferBuilder byteBufferBuilder;
	private BufferBuilder bufferBuilder;
	// The builder's native memory for the native= diagnostic; releasing it is what frees byteBufferBuilder
	private final VertexMemory.Account memory;
	private GpuBuffer vertexBuffer, indexBuffer;
	private int indexCount;
	// The vertex buffer's size on the GPU, for the gpu= diagnostic (GpuMemory)
	private long gpuBytes = 0;
	private int defaultR = 255, defaultG = 255, defaultB = 255, defaultA = 255;

	public ShapeBuffer() {
		byteBufferBuilder = new ByteBufferBuilder(initialBytes); //28 is lowest working (4 bytes * XYZ+RGBA). Number of blocks isn't always known, so it'll have to grow on its own
		memory = VertexMemory.open(initialBytes);
		bufferBuilder = new BufferBuilder(byteBufferBuilder, PrimitiveTopology.QUADS, DefaultVertexFormat.POSITION_COLOR);
	}
	
	public void setColour(int r, int g, int b, int a) {
		defaultR = r;
		defaultG = g;
		defaultB = b;
		defaultA = a;
	}
	
	public void pushVertex(double x, double y, double z) {
		memory.write(vertexBytes); // throws on a released buffer, before anything is written into freed memory
		bufferBuilder.addVertex((float) x, (float) y, (float) z).setColor(defaultR, defaultG, defaultB, defaultA);
	}

	/**
	 * Sends the vertices to the GPU and frees the native copy at once. createBuffer copies them (vanilla's
	 * SkyRenderer and CubeMap close their MeshData right after the same call) and nothing reads them again:
	 * render() draws the GPU buffer, with vanilla's shared index buffer. Called once, on the render thread.
	 */
	public void end() {
		if(memory.isReleased()) { // a discarded generation: nothing left to send (not reached in practice)
			indexCount = 0;
			return;
		}
		MeshData meshData = bufferBuilder.build();
		// null when nothing was pushed (a preview view that matches no cube): nothing to upload, render() draws nothing
		if(meshData == null) {
			indexCount = 0;
		}else {
			try {
				vertexBuffer = RenderSystem.getDevice().createBuffer(() -> "Build Guide vertices", GpuBuffer.USAGE_VERTEX, meshData.vertexBuffer());
				gpuBytes = vertexBuffer.size();
				GpuMemory.allocated(gpuBytes);
				indexCount = meshData.drawState().indexCount();
				indexBuffer = RenderSystem.getSequentialBuffer(PrimitiveTopology.QUADS).getBuffer(indexCount);
			}finally {
				meshData.close();
			}
		}
		releaseVertexData();
	}

	// The native copy, exactly once whoever calls first (end(), or Shape for a generation that never reaches the world)
	@Override
	public void releaseVertexData() {
		if(memory.release()) byteBufferBuilder.close();
	}

	// The GPU buffer only, on the render thread: close() also reaches buffers whose generation may still be
	// writing (Shape.update), so the native copy is never freed here
	public void close() {
		if(vertexBuffer != null) {
			vertexBuffer.close();
			GpuMemory.freed(gpuBytes);
		}
		vertexBuffer = null;
		// Don't also close indexBuffer, it is a reference to a global buffer used everywhere
	}

	/**
	 * Several buffers in one render pass (the world buffer's sections in view): the world pipeline, the current
	 * model-view and vanilla's shared index buffer, grown once for the largest, then one draw per buffer.
	 */
	public static void renderAll(List<ShapeBuffer> buffers) {
		RenderTarget renderTarget = Minecraft.getInstance().gameRenderer.mainRenderTarget();
		renderAll(buffers, renderTarget.getColorTextureView(), renderTarget.getDepthTextureView(), RenderHandler.getModelView(), RenderHandler.getRenderPipeline());
	}

	// The same into any target with an explicit model-view and pipeline (the 3D preview's sections)
	public static void renderAll(List<ShapeBuffer> buffers, GpuTextureView colourTexture, GpuTextureView depthTexture, Matrix4fc modelView, RenderPipeline pipeline) {
		int maxIndexCount = 0;
		for(ShapeBuffer buffer: buffers) if(buffer.vertexBuffer != null) maxIndexCount = Math.max(maxIndexCount, buffer.indexCount);
		if(maxIndexCount == 0) return;
		RenderSystem.AutoStorageIndexBuffer sequential = RenderSystem.getSequentialBuffer(PrimitiveTopology.QUADS);
		GpuBuffer indices = sequential.getBuffer(maxIndexCount);
		GpuBufferSlice dynamicTransforms = RenderSystem.getDynamicUniforms().writeTransform(new Matrix4f(modelView), new Vector4f(1.0f), new Vector3f(), new Matrix4f());
		try (RenderPass renderPass = RenderSystem.getDevice().createCommandEncoder().createRenderPass(() -> "Build Guide", colourTexture, Optional.empty(), depthTexture, OptionalDouble.empty())) {
			renderPass.setPipeline(RenderSystem.getCompiledPipeline(pipeline));
			RenderSystem.bindDefaultUniforms(renderPass);
			renderPass.setUniform("DynamicTransforms", dynamicTransforms);
			renderPass.setIndexBuffer(indices, sequential.type());
			for(ShapeBuffer buffer: buffers) {
				if(buffer.vertexBuffer == null || buffer.indexCount == 0) continue;
				renderPass.setVertexBuffer(0, buffer.vertexBuffer.slice());
				renderPass.drawIndexed(buffer.indexCount, 1, 0, 0, 0);
			}
		}
	}
	
	// World rendering: main render target, current model-view, the world pipeline (depth test toggle)
	public void render() {
		RenderTarget renderTarget = Minecraft.getInstance().gameRenderer.mainRenderTarget();
		render(renderTarget.getColorTextureView(), renderTarget.getDepthTextureView(), RenderHandler.getModelView(), RenderHandler.getRenderPipeline());
	}

	// Any target (the 3D preview draws into its picture-in-picture texture) with an explicit model-view and pipeline
	public void render(GpuTextureView colourTexture, GpuTextureView depthTexture, Matrix4fc modelView, RenderPipeline pipeline) {
		if(vertexBuffer == null || indexCount == 0) return;
		GpuBufferSlice dynamicTransforms = RenderSystem.getDynamicUniforms().writeTransform(new Matrix4f(modelView), new Vector4f(1.0f), new Vector3f(), new Matrix4f());
		try (RenderPass renderPass = RenderSystem.getDevice().createCommandEncoder().createRenderPass(() -> "Build Guide", colourTexture, Optional.empty(), depthTexture, OptionalDouble.empty())) {
			renderPass.setPipeline(RenderSystem.getCompiledPipeline(pipeline));
			RenderSystem.bindDefaultUniforms(renderPass);
			if(indexBuffer.isClosed()) {
				indexBuffer = RenderSystem.getSequentialBuffer(PrimitiveTopology.QUADS).getBuffer(indexCount);
			}
			renderPass.setUniform("DynamicTransforms", dynamicTransforms);
			renderPass.setIndexBuffer(indexBuffer, RenderSystem.getSequentialBuffer(PrimitiveTopology.QUADS).type());
			renderPass.setVertexBuffer(0, vertexBuffer.slice());
			renderPass.drawIndexed(indexCount, 1, 0, 0, 0);
		}
	}
}
