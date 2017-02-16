package com.wk.web.utils;

import java.io.*;

public abstract class FileUtil {

//    public static void copyFile(File srcFile, File destDir) {
//        if (srcFile.exists()) {
//            destDir.mkdirs();
//            InputStream i = getInput(srcFile);
//            write(i, new File(destDir, srcFile.getName()));
//        }
//    }

    public static long size(File file) {
        long count = 0;
        if (file.exists() && file.isFile()) {
            int len;
            byte[] b = new byte[1 << 10];
            FileInputStream i = null;
            try {
                i = new FileInputStream(file);
                while (-1 != (len = i.read(b))) {
                    count += len;
                }
            } catch (IOException e) {
                throw new RuntimeException(e);
            } finally {
                if(i!=null) {
                    try {
                        i.close();
                    } catch (IOException e) {
                        e.printStackTrace();
                    }
                }
            }
        }
        return count;
    }

    public static void delete(String filePath) {
        delete(new File(filePath));
    }

    public static void delete(File file) {
        if (file.exists()) {
            if (file.isDirectory() && file.listFiles() != null) {
                for (File sub : file.listFiles()) {
                    delete(sub);
                }
            }
            file.delete();
        }
    }

    public static void mkparent(File file) {
        if (!file.exists()) {
            File parent = file.getParentFile();
            if (!parent.exists() || !parent.isDirectory()) {
                parent.mkdirs();
            }
        }
    }

    public static OutputStream getOut(File file) {
        try {
            return new FileOutputStream(file);
        } catch (FileNotFoundException e) {
            throw new RuntimeException(e);
        }
    }

    public static InputStream getInput(File file) {
        try {
            return new FileInputStream(file);
        } catch (FileNotFoundException e) {
            throw new RuntimeException(e);
        }
    }

//    public static void write(InputStream input, File file) {
//        try {
//            FileOutputStream fos = new FileOutputStream(file);
//            StreamUtil.inputToOut(input, fos);
//        } catch (FileNotFoundException e) {
//            throw new RuntimeException(e);
//        }
//    }

//    public static void write(String text, String encoding, File file) {
//        try {
//            ByteArrayInputStream input = new ByteArrayInputStream(text.getBytes(encoding));
//            write(input, file);
//        } catch (UnsupportedEncodingException e) {
//            throw new RuntimeException(e);
//        }
//    }

    /**
     * 创建目录，如果存在的话直接返回目录，不存在的话创建
     */
    public static File createFolder(String s) {

        File f = new File(s);
        if (!f.exists() || !f.isDirectory()) {
            f.mkdirs();
        }

        return f;
    }
}
