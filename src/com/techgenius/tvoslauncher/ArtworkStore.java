package com.techgenius.tvoslauncher;

import android.graphics.Bitmap;
import java.io.*;
import java.lang.ref.WeakReference;
import java.security.MessageDigest;
import java.util.ArrayList;

/** Content-addressed artwork; cache keeps only bounded weak bitmap identities and paths. */
final class ArtworkStore {
    private static final ArrayList<Entry> cache = new ArrayList<>();
    private static final class Entry {
        final WeakReference<Bitmap> bitmap;
        final int generation;
        final File file;
        Entry(Bitmap b, File f) { bitmap=new WeakReference<>(b); generation=b.getGenerationId(); file=f; }
    }
    static synchronized String save(File dir, Bitmap bitmap) throws Exception {
        int generation=bitmap.getGenerationId();
        for (Entry e:cache) if(e.bitmap.get()==bitmap && e.generation==generation
                && e.file.getParentFile().equals(dir) && e.file.isFile()) return e.file.getAbsolutePath();
        float scale=Math.min(1f,960f/bitmap.getWidth());
        Bitmap small=scale<1f?Bitmap.createScaledBitmap(bitmap,Math.round(bitmap.getWidth()*scale),Math.round(bitmap.getHeight()*scale),true):bitmap;
        byte[] bytes;
        try(ByteArrayOutputStream out=new ByteArrayOutputStream()) {
            if(!small.compress(Bitmap.CompressFormat.JPEG,88,out)) throw new IOException("Cannot encode artwork");
            bytes=out.toByteArray();
        } finally { if(small!=bitmap) small.recycle(); }
        StringBuilder name=new StringBuilder();
        for(byte b:MessageDigest.getInstance("SHA-256").digest(bytes)) name.append(String.format(java.util.Locale.ROOT,"%02x",b&255));
        if(!dir.isDirectory()&&!dir.mkdirs()) throw new IOException("Cannot create artwork directory");
        File file=new File(dir,name+".jpg");
        if(!file.isFile()) {
            android.util.AtomicFile atomic=new android.util.AtomicFile(file);
            FileOutputStream out=atomic.startWrite();
            try { out.write(bytes); atomic.finishWrite(out); }
            catch(Exception e) { atomic.failWrite(out); throw e; }
        }
        cache.removeIf(e->e.bitmap.get()==null || e.bitmap.get()==bitmap);
        cache.add(new Entry(bitmap,file));
        while(cache.size()>32) cache.remove(0);
        return file.getAbsolutePath();
    }
}
