package ryuzuinfiniteshop.ryuzuinfiniteshop.migration;

import org.bukkit.configuration.file.YamlConfiguration;
import org.yaml.snakeyaml.DumperOptions;
import org.yaml.snakeyaml.LoaderOptions;
import org.yaml.snakeyaml.Yaml;
import org.yaml.snakeyaml.constructor.SafeConstructor;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.DataInputStream;
import java.io.DataOutputStream;
import java.io.IOException;
import java.util.ArrayList;
import java.util.Base64;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.zip.GZIPInputStream;
import java.util.zip.GZIPOutputStream;

/**
 * Restores player heads saved by 1.15.2 builds.
 * <p>
 * Old versions stored the head's profile only inside the base64 "internal" NBT of the item meta
 * (skull-owner held just the name). Newer servers ignore that NBT, so the heads came back blank.
 * ItemStacks are already deserialized by the time a {@link YamlConfiguration} is loaded, so the fix
 * has to run on the raw YAML text, see {@link #fixRawYaml(String)}.
 */
public final class LegacySkullMigration implements MigrationStep {
    public static final int FROM_VERSION = 2;
    public static final int TO_VERSION = 3;

    private static final String SKULL_OWNER = "SkullOwner";
    private static final String SKULL_PROFILE = "SkullProfile";

    @Override
    public int fromVersion() {
        return FROM_VERSION;
    }

    @Override
    public int toVersion() {
        return TO_VERSION;
    }

    @Override
    public String description() {
        return "Restore player head profiles saved by 1.15.2 from their legacy NBT";
    }

    @Override
    public void migrate(YamlConfiguration yaml) {
        // The actual conversion needs the raw text (see ShopMigrationService); only the version is bumped here.
        yaml.set("data-version", TO_VERSION);
    }

    /**
     * @return the converted YAML text, or the input itself when no legacy head was found.
     */
    public static String fixRawYaml(String text) {
        Object root;
        try {
            root = new Yaml(new SafeConstructor(new LoaderOptions())).load(text);
        } catch (RuntimeException e) {
            return text;
        }
        if (!fixNode(root)) return text;

        DumperOptions options = new DumperOptions();
        options.setDefaultFlowStyle(DumperOptions.FlowStyle.BLOCK);
        options.setSplitLines(false);
        options.setWidth(Integer.MAX_VALUE);
        return new Yaml(options).dump(root);
    }

    @SuppressWarnings("unchecked")
    private static boolean fixNode(Object node) {
        boolean changed = false;
        if (node instanceof Map<?, ?> map) {
            Map<Object, Object> m = (Map<Object, Object>) map;
            if ("SKULL".equals(m.get("meta-type")) && fixSkullMeta(m)) changed = true;
            for (Object value : m.values()) changed |= fixNode(value);
        } else if (node instanceof List<?> list) {
            for (Object value : list) changed |= fixNode(value);
        }
        return changed;
    }

    private static boolean fixSkullMeta(Map<Object, Object> meta) {
        if (!(meta.get("internal") instanceof String internal) || meta.get("skull-owner") instanceof Map) return false;

        Map<String, Object> nbt;
        try {
            nbt = readNbt(Base64.getDecoder().decode(internal.replaceAll("\\s", "")));
        } catch (IOException | RuntimeException e) {
            return false;
        }
        // 1.15.2 Paper stored the profile under "SkullProfile"; vanilla used "SkullOwner".
        Object ownerTag = nbt.get(SKULL_OWNER) != null ? nbt.get(SKULL_OWNER) : nbt.get(SKULL_PROFILE);
        if (!(ownerTag instanceof Map<?, ?> owner)) return false;

        Map<String, Object> profile = new LinkedHashMap<>();
        profile.put("==", "PlayerProfile");
        Object id = owner.get("Id");
        UUID uuid = toUuid(id);
        // CraftPlayerProfile.deserialize reads "uniqueId" (not "uuid")
        if (uuid != null) profile.put("uniqueId", uuid.toString());
        if (owner.get("Name") instanceof String name && !name.isBlank()) profile.put("name", name);

        List<Map<String, Object>> properties = new ArrayList<>();
        if (owner.get("Properties") instanceof Map<?, ?> props) {
            for (Map.Entry<?, ?> entry : props.entrySet()) {
                if (!(entry.getValue() instanceof List<?> values)) continue;
                for (Object value : values) {
                    if (!(value instanceof Map<?, ?> v) || !(v.get("Value") instanceof String data)) continue;
                    Map<String, Object> property = new LinkedHashMap<>();
                    property.put("name", String.valueOf(entry.getKey()));
                    property.put("value", data);
                    if (v.get("Signature") instanceof String signature) property.put("signature", signature);
                    properties.add(property);
                }
            }
        }
        if (!properties.isEmpty()) profile.put("properties", properties);
        if (!profile.containsKey("uniqueId") && !profile.containsKey("name")) {
            if (properties.isEmpty()) return false;
            // A profile needs a uuid or a name; derive a stable one from the texture
            String texture = String.valueOf(properties.get(0).get("value"));
            profile.put("uniqueId", UUID.nameUUIDFromBytes(texture.getBytes(java.nio.charset.StandardCharsets.UTF_8)).toString());
        }

        meta.put("skull-owner", profile);

        // Paper parses a leftover SkullProfile/SkullOwner from "internal" with the current codec, which cannot read
        // the 1.15.2 layout and yields an empty profile; that empty profile then wins over "skull-owner".
        nbt.remove(SKULL_OWNER);
        nbt.remove(SKULL_PROFILE);
        if (nbt.isEmpty()) {
            meta.remove("internal");
        } else {
            try {
                meta.put("internal", Base64.getEncoder().encodeToString(writeNbt(nbt)));
            } catch (IOException e) {
                meta.remove("internal");
            }
        }
        return true;
    }

    private static UUID toUuid(Object id) {
        try {
            if (id instanceof String s) return UUID.fromString(s);
            if (id instanceof int[] a && a.length == 4) {
                return new UUID((long) a[0] << 32 | (a[1] & 0xFFFFFFFFL), (long) a[2] << 32 | (a[3] & 0xFFFFFFFFL));
            }
        } catch (IllegalArgumentException ignored) {
        }
        return null;
    }

    // ---- minimal NBT reader (gzip, big endian) ----

    static Map<String, Object> readNbt(byte[] data) throws IOException {
        try (DataInputStream in = new DataInputStream(new GZIPInputStream(new ByteArrayInputStream(data)))) {
            if (in.readByte() != 10) throw new IOException("root is not a compound");
            in.readUTF();
            return readCompound(in);
        }
    }

    private static Map<String, Object> readCompound(DataInputStream in) throws IOException {
        Map<String, Object> result = new LinkedHashMap<>();
        byte type;
        while ((type = in.readByte()) != 0) {
            String name = in.readUTF();
            result.put(name, readTag(in, type));
        }
        return result;
    }

    private static Object readTag(DataInputStream in, byte type) throws IOException {
        switch (type) {
            case 1: return in.readByte();
            case 2: return in.readShort();
            case 3: return in.readInt();
            case 4: return in.readLong();
            case 5: return in.readFloat();
            case 6: return in.readDouble();
            case 7: {
                byte[] bytes = new byte[in.readInt()];
                in.readFully(bytes);
                return bytes;
            }
            case 8: return in.readUTF();
            case 9: {
                byte elementType = in.readByte();
                int size = in.readInt();
                NbtList list = new NbtList(elementType);
                for (int i = 0; i < size; i++) list.add(readTag(in, elementType));
                return list;
            }
            case 10: return readCompound(in);
            case 11: {
                int[] ints = new int[in.readInt()];
                for (int i = 0; i < ints.length; i++) ints[i] = in.readInt();
                return ints;
            }
            case 12: {
                long[] longs = new long[in.readInt()];
                for (int i = 0; i < longs.length; i++) longs[i] = in.readLong();
                return longs;
            }
            default: throw new IOException("unknown tag type " + type);
        }
    }

    /** A list tag that remembers its element type, so it can be written back (even when empty). */
    static final class NbtList extends ArrayList<Object> {
        final byte elementType;

        NbtList(byte elementType) {
            this.elementType = elementType;
        }
    }

    // ---- minimal NBT writer, the inverse of the reader ----

    static byte[] writeNbt(Map<String, Object> root) throws IOException {
        ByteArrayOutputStream bytes = new ByteArrayOutputStream();
        try (DataOutputStream out = new DataOutputStream(new GZIPOutputStream(bytes))) {
            out.writeByte(10);
            out.writeUTF("");
            writeCompound(out, root);
        }
        return bytes.toByteArray();
    }

    private static void writeCompound(DataOutputStream out, Map<?, ?> compound) throws IOException {
        for (Map.Entry<?, ?> entry : compound.entrySet()) {
            out.writeByte(typeOf(entry.getValue()));
            out.writeUTF(String.valueOf(entry.getKey()));
            writeTag(out, entry.getValue());
        }
        out.writeByte(0);
    }

    private static byte typeOf(Object value) throws IOException {
        if (value instanceof Byte) return 1;
        if (value instanceof Short) return 2;
        if (value instanceof Integer) return 3;
        if (value instanceof Long) return 4;
        if (value instanceof Float) return 5;
        if (value instanceof Double) return 6;
        if (value instanceof byte[]) return 7;
        if (value instanceof String) return 8;
        if (value instanceof List) return 9;
        if (value instanceof Map) return 10;
        if (value instanceof int[]) return 11;
        if (value instanceof long[]) return 12;
        throw new IOException("unsupported tag value " + value);
    }

    private static void writeTag(DataOutputStream out, Object value) throws IOException {
        switch (typeOf(value)) {
            case 1 -> out.writeByte((Byte) value);
            case 2 -> out.writeShort((Short) value);
            case 3 -> out.writeInt((Integer) value);
            case 4 -> out.writeLong((Long) value);
            case 5 -> out.writeFloat((Float) value);
            case 6 -> out.writeDouble((Double) value);
            case 7 -> {
                byte[] bytes = (byte[]) value;
                out.writeInt(bytes.length);
                out.write(bytes);
            }
            case 8 -> out.writeUTF((String) value);
            case 9 -> {
                List<?> list = (List<?>) value;
                byte elementType = value instanceof NbtList nbtList ? nbtList.elementType
                        : list.isEmpty() ? 0 : typeOf(list.get(0));
                out.writeByte(elementType);
                out.writeInt(list.size());
                for (Object element : list) writeTag(out, element);
            }
            case 10 -> writeCompound(out, (Map<?, ?>) value);
            case 11 -> {
                int[] ints = (int[]) value;
                out.writeInt(ints.length);
                for (int i : ints) out.writeInt(i);
            }
            case 12 -> {
                long[] longs = (long[]) value;
                out.writeInt(longs.length);
                for (long l : longs) out.writeLong(l);
            }
            default -> throw new IOException("unsupported tag value " + value);
        }
    }
}
