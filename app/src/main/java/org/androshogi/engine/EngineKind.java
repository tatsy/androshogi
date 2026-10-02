package org.androshogi.engine;

/** NNUE architectures supported by the bundled YaneuraOu executables. */
public enum EngineKind {
    HALFKP_256X2_32_32("halfkp_256x2_32_32", 16),
    HALFKP_512X2_8_64("halfkp_512x2_8_64", 40),
    HALFKP_768X2_16_64("halfkp_768x2_16_64", 40);

    public static final EngineKind DEFAULT = HALFKP_256X2_32_32;
    private final String id;
    private final int defaultFvScale;

    EngineKind(String id, int defaultFvScale) {
        this.id = id;
        this.defaultFvScale = defaultFvScale;
    }

    public String id() {
        return id;
    }

    public String executableName(String abi) {
        return "YaneuraOu_NNUE_" + id + "_" + abi;
    }

    public int defaultFvScale() {
        return defaultFvScale;
    }

    /** Old installations and unknown preference values use the original engine. */
    public static EngineKind fromId(String id) {
        for (EngineKind kind : values()) {
            if (kind.id.equals(id)) {
                return kind;
            }
        }
        return DEFAULT;
    }
}
