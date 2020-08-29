package com.dandan.project.download;

import org.bouncycastle.jce.provider.BouncyCastleProvider;

import java.security.Security;

public class M3u8DownloadFactory {

    private static M3u8Download m3u8Download;

    /**
     *
     * 解决java不支持AES/CBC/PKCS7Padding模式解密
     *
     */
    static {
        Security.addProvider(new BouncyCastleProvider());
    }



    /**
     * 获取实例
     *
     * @param downloadUrl 要下载的链接
     * @return 返回m3u8下载实例
     */
    public static M3u8Download getInstance(String downloadUrl) {
        if (m3u8Download == null) {
            synchronized (M3u8Download.class) {
                if (m3u8Download == null)
                    m3u8Download = new M3u8Download(downloadUrl);
            }
        }
        return m3u8Download;
    }

    public static void destroied() {
        m3u8Download = null;
    }

}
