package com.xxq.xlai;

public class CustomModel {
    public int id;
    public String name;
    public String ggufFilename;
    public String mmprojFilename;
    public boolean hasVision;
    public boolean isDownloaded;

    public CustomModel(int id, String name, String ggufFilename, String mmprojFilename, boolean hasVision, boolean isDownloaded) {
        this.id = id;
        this.name = name;
        this.ggufFilename = ggufFilename;
        this.mmprojFilename = mmprojFilename;
        this.hasVision = hasVision;
        this.isDownloaded = isDownloaded;
    }
}
