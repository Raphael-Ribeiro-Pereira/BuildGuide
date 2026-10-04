package brentmaas.buildguide.common;

import java.io.BufferedReader;
import java.io.File;
import java.io.IOException;
import java.io.UnsupportedEncodingException;
import java.io.Writer;
import java.net.URLDecoder;
import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.nio.file.AtomicMoveNotSupportedException;
import java.nio.file.Files;
import java.nio.file.StandardCopyOption;
import java.util.LinkedHashMap;
import java.util.Map;

/**
 * Shape presets (GUI redesign E8): numSlots global slots, the same in every world, in their own
 * file next to buildguide.cfg (Config rewrites its file with its own keys only, so it would drop
 * them). One line per occupied slot, "slotN=<data>", N from 1. The data is opaque here: ShapeSet
 * writes and reads it in its own "key=value;" persistence format, so unknown keys are skipped and
 * a preset from an older jar (fewer values) still loads. Written on every change, whatever
 * persistenceEnabled says (saving a preset is an explicit action), through a temporary file moved
 * over the old one so a crash mid-write never corrupts the slots.
 *
 * Only java.*: no net.minecraft.
 */
public class PresetStore {
	public static final int numSlots = 3;
	public static final String fileName = "buildguide_presets.txt";
	private static final String slotPrefix = "slot";
	// Keys only presets have (ShapeSet writes them, the world save never does)
	public static final String KEY_WORLD = "world";
	private static final String[] setKeys = {"index", "origin", "exclusions", "name", KEY_WORLD};

	private final File file;
	private final String[] slots = new String[numSlots];

	// folder null: in memory only (tests)
	public PresetStore(File folder) {
		file = folder == null ? null : new File(folder, fileName);
		load();
	}

	public synchronized String get(int slot) {
		return slots[slot];
	}

	public synchronized boolean isEmpty(int slot) {
		return slots[slot] == null;
	}

	// Store (or overwrite) a slot and write the file; data must be one line
	public synchronized void set(int slot, String data) throws IOException {
		if(data == null || data.isEmpty() || data.indexOf('\n') >= 0 || data.indexOf('\r') >= 0) throw new IllegalArgumentException("preset data must be one non-empty line");
		slots[slot] = data;
		save();
	}

	public synchronized void clear(int slot) throws IOException {
		slots[slot] = null;
		save();
	}

	// Missing file: all empty. Unreadable or malformed lines are skipped, the others still load
	private synchronized void load() {
		if(file == null || !file.exists()) return;
		try(BufferedReader reader = Files.newBufferedReader(file.toPath(), StandardCharsets.UTF_8)) {
			String line;
			while((line = reader.readLine()) != null) parseLine(line, slots);
		}catch(IOException e) {
			if(BuildGuide.logHandler != null) BuildGuide.logHandler.error("Build Guide presets failed to load: " + e.getMessage());
		}
	}

	// "slotN=data" into slots[N - 1]; anything else is ignored
	public static void parseLine(String line, String[] slots) {
		int separatorIndex = line.indexOf('=');
		if(separatorIndex <= slotPrefix.length() || !line.startsWith(slotPrefix)) return;
		int n;
		try {
			n = Integer.parseInt(line.substring(slotPrefix.length(), separatorIndex));
		}catch(NumberFormatException e) {
			return;
		}
		String data = line.substring(separatorIndex + 1);
		if(n >= 1 && n <= slots.length && !data.isEmpty()) slots[n - 1] = data;
	}

	private void save() throws IOException {
		if(file == null) return;
		StringBuilder text = new StringBuilder();
		for(int i = 0;i < numSlots;++i) if(slots[i] != null) text.append(slotPrefix).append(i + 1).append('=').append(slots[i]).append('\n');
		File parent = file.getAbsoluteFile().getParentFile();
		if(parent != null && !parent.exists()) parent.mkdirs();
		File temp = new File(parent, fileName + ".tmp");
		try(Writer writer = Files.newBufferedWriter(temp.toPath(), StandardCharsets.UTF_8)) {
			writer.write(text.toString());
		}
		// File.renameTo fails on Windows when the target exists (every save after the first)
		try {
			Files.move(temp.toPath(), file.toPath(), StandardCopyOption.REPLACE_EXISTING, StandardCopyOption.ATOMIC_MOVE);
		}catch(AtomicMoveNotSupportedException e) {
			Files.move(temp.toPath(), file.toPath(), StandardCopyOption.REPLACE_EXISTING);
		}
	}

	// Free text (instance name, world) inside a "key=value;" entry: ';', '=', '%' and line breaks encoded
	public static String encode(String text) {
		try {
			return URLEncoder.encode(text, "UTF-8");
		}catch(UnsupportedEncodingException e) {
			throw new IllegalStateException(e);
		}
	}

	public static String decode(String text) {
		try {
			return URLDecoder.decode(text, "UTF-8");
		}catch(UnsupportedEncodingException | IllegalArgumentException e) {
			return text;
		}
	}

	// Parts of a slot label: {shape class, instance name or null, world or ""}. The shape class is the one
	// key that is not a set key (null if there is none); the caller checks it against the registry
	public static String[] describe(String data) {
		Map<String, String> e = entries(data);
		String shapeClass = null;
		for(String key: e.keySet()) {
			boolean setKey = false;
			for(String k: setKeys) if(k.equals(key)) setKey = true;
			if(!setKey) {
				shapeClass = key;
				break;
			}
		}
		String name = e.containsKey("name") ? decode(e.get("name")) : null;
		if(name != null && name.trim().isEmpty()) name = null;
		return new String[] {shapeClass, name, e.containsKey(KEY_WORLD) ? decode(e.get(KEY_WORLD)) : ""};
	}
	
	// The "key=value;" entries of preset data, in order (first '=' splits; later duplicates win)
	public static Map<String, String> entries(String data) {
		Map<String, String> result = new LinkedHashMap<String, String>();
		if(data == null) return result;
		for(String entry: data.split(";")) {
			int separatorIndex = entry.indexOf('=');
			if(separatorIndex > 0) result.put(entry.substring(0, separatorIndex), entry.substring(separatorIndex + 1));
		}
		return result;
	}
}
