package com.niuqu.pickupcard.render.nvg;

import com.niuqu.pickupcard.PickupCard;

import java.net.URL;
import java.net.URLClassLoader;

/** 临时：定位 NanoVG 在 Forge dev 里为什么加载不到。定位完删掉。 */
final class NvgDiagnostics {

    private static final String NL = System.lineSeparator();
    private static boolean done;

    private NvgDiagnostics() {
    }

    static synchronized void report(String className) {
        if (done) {
            return;
        }
        done = true;
        StringBuilder sb = new StringBuilder("nvg 诊断:" + NL);
        for (ClassLoader cl = NvgDiagnostics.class.getClassLoader(); cl != null; cl = cl.getParent()) {
            sb.append("loader ").append(cl.getClass().getName()).append(NL);
            if (cl instanceof URLClassLoader ucl) {
                for (URL u : ucl.getURLs()) {
                    sb.append("   url ").append(u).append(NL);
                }
            }
            try {
                Class.forName(className, false, cl);
                sb.append("   ^^^ 这个 loader 能找到").append(NL);
            } catch (Throwable e) {
                sb.append("   ^^^ 找不到: ").append(e).append(NL);
            }
        }
        String legacyFile = System.getProperty("legacyClassPath.file");
        if (legacyFile != null) {
            try {
                String text = java.nio.file.Files.readString(java.nio.file.Path.of(legacyFile));
                sb.append("legacy 文件里有 nanovg: ").append(text.toLowerCase(java.util.Locale.ROOT).contains("nanovg")).append(NL);
                String[] lines = text.split(NL);
                sb.append("legacy 最后一行: ").append(lines.length == 0 ? "<空>" : lines[lines.length - 1]).append(NL);
            } catch (Throwable e) {
                sb.append("读 legacy 文件失败: ").append(e).append(NL);
            }
        }
        sb.append("java.class.path=").append(System.getProperty("java.class.path")).append(NL);
        sb.append("legacyClassPath.file=").append(System.getProperty("legacyClassPath.file")).append(NL);
        PickupCard.LOGGER.error(sb.toString());
    }
}
