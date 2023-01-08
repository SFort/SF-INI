package tf.ssf.sfort.ini;

import java.io.BufferedReader;
import java.io.File;
import java.io.FileInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.io.Reader;
import java.io.StringReader;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.StringJoiner;
import java.util.function.Consumer;

//mostly a dumbed down version of QDIni with some minor additions
public class SFIni {
	public static class Data {
		public List<String> comments;
		public String val;
		public Data (String val, List<String> comments) {
			this.val = val;
			this.comments = comments;
		}
	}

	public LinkedHashMap<String, List<Data>> data = new LinkedHashMap<>();

	public SFIni() {}

	public boolean containsKey(String key) {
		List<Data> val = data.get(key);
		return val != null && !val.isEmpty();
	}

	public String getLast(String key) {
		List<Data> val = data.get(key);
		if (val == null || val.isEmpty()) return null;
		return val.get(val.size()-1).val;
	}

	public int getInt(String key) throws IllegalArgumentException {
		try {
			return Integer.parseInt(getLast(key));
		} catch (IllegalArgumentException e) {
			throw new IllegalArgumentException("Failed to parse as int (key: "+key+")", e);
		}
	}

	public double getDouble(String key) throws IllegalArgumentException {
		try {
			return Double.parseDouble(getLast(key));
		} catch (IllegalArgumentException e) {
			throw new IllegalArgumentException("Failed to parse as double (key: "+key+")", e);
		}
	}

	public boolean getBoolean(String key) throws IllegalArgumentException {
		String val = getLast(key);
		if (val == null) throw new IllegalArgumentException("Failed to parse as bool (key: "+key+") no values found");
		switch (val.toLowerCase(Locale.ROOT)) {
			case "1": case "true": return true;
			case "0": case "false": return false;
			default: throw new IllegalArgumentException("Failed to parse as bool (key: \"+key+\") valid values: true, false, 1, 0");
		}
	}

	public <E extends Enum<E>> E getEnum(String key, Class<E> clazz) throws IllegalArgumentException {
		String val = getLast(key);
		if (val == null) throw new IllegalArgumentException("Failed to parse as "+clazz.getName()+" (key: "+key+") no values found");
		try {
			return Enum.valueOf(clazz, val.toUpperCase(Locale.ROOT));
		} catch (IllegalArgumentException e) {
			StringJoiner joiner = new StringJoiner(", ");
			for(E en : clazz.getEnumConstants()) {
				joiner.add(en.name());
			}
			String joined = joiner.toString();
			throw new IllegalArgumentException("Failed to parse as "+clazz.getName()+" (key: "+key+") valid values: "+joined+", "+joined.toLowerCase(Locale.ROOT), e);
		}
	}

	@Override
	public String toString() {
		StringBuilder sb = new StringBuilder();
		writeToStringConsumer(sb::append);
		return sb.toString();
	}

	public void writeToStringConsumer(Consumer<String> consumer) {
		for (Map.Entry<String, List<Data>> en : data.entrySet()) {
			for (Data v : en.getValue()) {
				for (String com : v.comments) {
					consumer.accept(";");
					consumer.accept(com);
					consumer.accept("\r\n");
				}
				consumer.accept(en.getKey());
				consumer.accept("=");
				consumer.accept(v.val);
				consumer.accept("\r\n");
			}
		}
	}

	public void load(String s) {
		try {
			load(new StringReader(s));
		} catch (IOException e) {
			throw new AssertionError(e);
		}
	}

	public void load(File f) throws IOException {
		try (InputStream in = new FileInputStream(f)) {
			load(in);
		}
	}

	public void load(Path p) throws IOException {
		try (InputStream in = Files.newInputStream(p)) {
			load(in);
		}
	}

	public void load(InputStream in) throws IOException {
		load(new InputStreamReader(in, StandardCharsets.UTF_8));
	}

	public void load(Reader r) throws IOException, IllegalArgumentException {
		BufferedReader br = r instanceof BufferedReader ? (BufferedReader)r : new BufferedReader(r);
		int lineNum = 1;
		String seqKey = null;
		List<String> seqComments = new ArrayList<>();
		String path = "";
		while (true) {
			String line = br.readLine();
			if (line == null) break;
			String trunc = line.trim();
			if (trunc.startsWith(";")) {
				lineNum++;
				seqComments.add(trunc);
				continue;
			}
			if (trunc.isEmpty()) {
				lineNum++;
				continue;
			}
			if (line.startsWith("[")) {
				seqKey = null;
				seqComments.clear();
				if (line.contains(";")) {
					throw new IllegalArgumentException("Malformed section header at line "+lineNum);
				}
				if (trunc.endsWith("]")) {
					String newPath = trunc.substring(1, trunc.length()-1);
					if (newPath.contains("[") || newPath.contains("]")) {
						throw new IllegalArgumentException("Malformed section header at line "+lineNum);
					}
					if (!(newPath.isEmpty() || newPath.endsWith("."))) {
						newPath += ".";
					}
					if (newPath.startsWith(".")){
						path += newPath;
					} else {
						path = newPath;
					}
				} else {
					throw new IllegalArgumentException("Malformed section header at line "+lineNum);
				}
				lineNum++;
				continue;
			}
			if (line.contains("=")) {
				int equals = trunc.indexOf('=');

				String key;
				if (equals == 1 && trunc.charAt(0) == '.') {
					if (seqKey == null) {
						throw new IllegalArgumentException("Couldn't find a section, comment, or key-value assigment at line "+lineNum);
					}
					key = seqKey;
				} else {
					seqKey = key = path+trunc.substring(0, equals);
				}
				String value = trunc.substring(equals+1);
				List<String> seqCom;
				if (!seqComments.isEmpty()) {
					seqCom = seqComments;
					seqComments = new ArrayList<>();
				} else {
					seqCom = null;
				}
				this.data.compute(key, (k, l) -> {
					if (l == null) l = new ArrayList<>();
					l.add( new Data(value, seqCom));
					return l;
				});
			} else {
				throw new IllegalArgumentException("Couldn't find a section, comment, or key-value assigment at line "+lineNum);
			}
			lineNum++;
		}
	}
}
