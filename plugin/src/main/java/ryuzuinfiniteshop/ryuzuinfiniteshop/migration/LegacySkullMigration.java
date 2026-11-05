package ryuzuinfiniteshop.ryuzuinfiniteshop.migration;

import org.bukkit.configuration.file.YamlConfiguration;
import org.yaml.snakeyaml.DumperOptions;
import org.yaml.snakeyaml.LoaderOptions;
import org.yaml.snakeyaml.Yaml;
import org.yaml.snakeyaml.constructor.SafeConstructor;

import java.io.ByteArrayInputStream;
import java.io.DataInputStream;
import java.io.IOException;
import java.util.ArrayList;
import java.util.Base64;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.zip.GZIPInputStream;

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
        // Some 1.15.2 plugins (and Paper) stored the profile under "SkullProfile" instead of "SkullOwner".
        Object ownerTag = nbt.get(SKULL_OWNER) != null ? nbt.get(SKULL_OWNER) : nbt.get("SkullProfile");
        if (!(ownerTag instanceof Map<?, ?> owner)) return false;

        Map<String, Object> profile = new LinkedHashMap<>();
        profile.put("==", "PlayerProfile");
        Object id = owner.get("Id");
        UUID uuid = toUuid(id);
        if (uuid != null) profile.put("uuid", uuid.toString());
        if (owner.get("Name") instanceof String name) profile.put("name", name);

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
        if (!profile.containsKey("uuid") && !profile.containsKey("name") && properties.isEmpty()) return false;

        meta.put("skull-owner", profile);
        if (nbt.size() == 1) meta.remove("internal");
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
                List<Object> list = new ArrayList<>();
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
}
