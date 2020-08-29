package com.dandan.project.main;


import cn.hutool.core.collection.ConcurrentHashSet;
import cn.hutool.core.date.DateUtil;
import cn.hutool.core.io.FileUtil;
import cn.hutool.core.io.IoUtil;
import cn.hutool.core.thread.ThreadUtil;
import cn.hutool.core.util.URLUtil;
import cn.hutool.http.HttpUtil;
import cn.hutool.log.Log;
import cn.hutool.log.LogFactory;
import com.dandan.project.download.M3u8Download;
import com.dandan.project.download.M3u8DownloadFactory;
import com.dandan.project.exception.M3u8Exception;
import com.dandan.project.listener.DownloadListener;
import com.dandan.project.util.Constant;
import com.dandan.project.util.StringUtils;

import java.io.*;
import java.nio.charset.Charset;
import java.util.HashSet;
import java.util.Set;
import java.util.concurrent.*;
import java.util.concurrent.atomic.AtomicInteger;

public class M3u8Main {

    private static final String M3U8URL = "https://cctvbsh5c.v.live.baishancdnx.cn/live/cdrmcctv2_1_1800.m3u8";
    public static Log log = LogFactory.get(M3u8Main.class);
    private static final String DIR = "/Users/baoyongtao/m3u8";
    public static volatile boolean isEnd = false;
    //所有ts片段下载链接
    private static LinkedBlockingQueue<String> queue = new LinkedBlockingQueue<String>(1000);
    private static Set<String> fileSet = new HashSet<>();


    //解密后的片段
    private static Set<File> finishedFiles = new ConcurrentHashSet<>();


    //解密算法名称
    private static String method;

    //密钥
    private static String key = "";

    //密钥字节
    private static byte[] keyBytes = new byte[16];
    //IV
    private static String iv = "";

    public static void download(String name,Long second) {


        M3u8Download m3u8Download = M3u8DownloadFactory.getInstance(M3U8URL);
        //设置生成目录
        m3u8Download.setDir(DIR);
        //设置视频名称
        m3u8Download.setFileName(name);
        //设置线程数
        m3u8Download.setThreadCount(Runtime.getRuntime().availableProcessors()*2);
        //设置重试次数
        m3u8Download.setRetryCount(100);
        //设置连接超时时间（单位：毫秒）
        m3u8Download.setTimeoutMillisecond(second);
        /*
        设置日志级别
        可选值：NONE INFO DEBUG ERROR
        */
        m3u8Download.setLogLevel(Constant.INFO);
        //设置监听器间隔（单位：毫秒）
        m3u8Download.setInterval(500L);
        //添加监听器
        m3u8Download.addListener(new DownloadListener() {
            @Override
            public void start() {
                System.out.println("开始下载！");
            }

            @Override
            public void process(String downloadUrl, int finished, int sum, float percent) {
                System.out.println("下载网址：" + downloadUrl + "\t已下载" + finished + "个\t一共" + sum + "个\t已完成" + percent + "%");
            }

            @Override
            public void speed(String speedPerSecond) {
                System.out.println("下载速度：" + speedPerSecond);
            }

            @Override
            public void end() {
                while (!isEnd) {
                    try {
                        TimeUnit.SECONDS.sleep(1);
                    } catch (InterruptedException e) {
                        e.printStackTrace();
                    }
                }
                download(name, second);
            }
        });
        //开始下载
        m3u8Download.start();
    }

    public static void main(String[] args) throws InterruptedException {
        while (true) {
            getTsUrl(M3U8URL);
            startDownload();
            TimeUnit.SECONDS.sleep(8);
        }

    }


    public static Thread download(String urls, int i) {
        return new Thread(() -> {
            String path = DIR + Constant.FILESEPARATOR + i + ".xy";
            //xy为未解密的ts片段，如果存在，则删除
            File file = new File(path);
            if (file.exists())
                return;
            //模拟http请求获取ts片段文件
            log.info("开始下载：[{}],位置:[{}]", urls, file.getAbsolutePath());
            InputStream inputStream = URLUtil.getStream(URLUtil.url(urls));
            OutputStream outputStream = null;
            try {
                outputStream = new FileOutputStream(file);
                IoUtil.read(inputStream).writeTo(outputStream);
                finishedFiles.add(file);
            } catch (FileNotFoundException e) {
                e.printStackTrace();
            } finally {
                IoUtil.close(outputStream);
            }
        });
    }

    /**
     * 删除下载好的片段
     */
    private static void deleteFiles() {
        File file = new File(DIR);
        for (File f : file.listFiles()) {
            if (f.getName().endsWith(".xy") || f.getName().endsWith(".xyz"))
                f.delete();
        }
    }


    public static Thread getTask() {
        return new Thread(() -> {
            try {
                File file = new File(DIR + Constant.FILESEPARATOR + DateUtil.currentSeconds() + ".mp4");
                if (file.exists())
                    file.delete();
                else {
                    file.createNewFile();
                }
                FileOutputStream fileOutputStream = new FileOutputStream(file);
                for (File f : finishedFiles) {
                    InputStream fileInputStream = new FileInputStream(f);
                    IoUtil.read(fileInputStream).writeTo(fileOutputStream);
                    fileOutputStream.flush();
                }
                fileOutputStream.close();
            } catch (IOException e) {
                e.printStackTrace();
            } finally {
                for (File finishedFile : finishedFiles) {
                    FileUtil.del(finishedFile);
                }
                log.info("视频合并完成，欢迎使用!");
            }
        });

    }


    /**
     * 合并下载好的ts片段
     */
    private static void mergeTs() {
        try {
            File file = new File(DIR + Constant.FILESEPARATOR + DateUtil.currentSeconds() + ".mp4");
            if (file.exists())
                file.delete();
            else {
                file.createNewFile();
            }
            FileOutputStream fileOutputStream = new FileOutputStream(file);
            for (File f : finishedFiles) {
                InputStream fileInputStream = new FileInputStream(f);
                IoUtil.read(fileInputStream).writeTo(fileOutputStream);
                fileOutputStream.flush();
            }
            fileOutputStream.close();
        } catch (IOException e) {
            e.printStackTrace();
        } finally {
            for (File finishedFile : finishedFiles) {
                FileUtil.del(finishedFile);
            }
            log.info("视频合并完成，欢迎使用!");
        }
    }
        public static void startDownload () {
            //线程池
            final ExecutorService fixedThreadPool = Executors.newFixedThreadPool(3);
            //如果生成目录不存在，则创建
            File file1 = new File(DIR);
            if (!file1.exists())
                file1.mkdirs();
            //执行多线程下载
//        CountDownLatch countDownLatch = new CountDownLatch(queue.size());
            AtomicInteger atomicInteger = new AtomicInteger(0);
            for (String s : queue) {
                int i = atomicInteger.incrementAndGet();
                fixedThreadPool.submit(() -> {
                    download(s, i).start();
                });
            }
            //开始合并视频
            mergeTs();
            //删除多余的ts片段
            deleteFiles();

            queue.clear();
            fixedThreadPool.shutdown();
        }


        public static String getTsUrl (String url) throws InterruptedException {
            String content = HttpUtil.downloadString(url, Charset.defaultCharset());
            log.info("获取下载链接:[{}],内容:[{}]", url, content);
            //判断是否是m3u8链接
            if (!content.contains("#EXTM3U")) {
                throw new M3u8Exception(url + "不是m3u8链接！");
            }
            String[] split = content.split("\\n");
            String keyUrl = "";
            boolean isKey = false;
            for (String s : split) {
                //如果含有此字段，则说明只有一层m3u8链接
                if (s.contains("#EXT-X-KEY") || s.contains("#EXTINF")) {
                    isKey = true;
                    keyUrl = url;
                    break;
                }
                //如果含有此字段，则说明ts片段链接需要从第二个m3u8链接获取
                if (s.contains(".m3u8")) {
                    if (StringUtils.isUrl(s))
                        return s;
                    String relativeUrl = url.substring(0, url.lastIndexOf("/") + 1);
                    if (s.startsWith("/"))
                        s = s.replaceFirst("/", "");
                    keyUrl = mergeUrl(relativeUrl, s);
                    break;
                }
            }
            if (StringUtils.isEmpty(keyUrl))
                throw new M3u8Exception("未发现key链接！");
            //获取密钥
            String key1 = isKey ? getKey(keyUrl, content) : getKey(keyUrl, null);
            if (StringUtils.isNotEmpty(key1))
                key = key1;
            else key = null;
            return key;
        }

        public static String getKey (String url, String content) throws InterruptedException {
            String urlContent;
            if (content == null || StringUtils.isEmpty(content))
                urlContent = HttpUtil.downloadString(url, Charset.defaultCharset());
            else urlContent = content;
            if (!urlContent.contains("#EXTM3U"))
                throw new M3u8Exception(url + "不是m3u8链接！");
            String[] split = urlContent.split("\\n");
            for (String s : split) {
                //如果含有此字段，则获取加密算法以及获取密钥的链接
                if (s.contains("EXT-X-KEY")) {
                    String[] split1 = s.split(",");
                    for (String s1 : split1) {
                        if (s1.contains("METHOD")) {
                            method = s1.split("=", 2)[1];
                            continue;
                        }
                        if (s1.contains("URI")) {
                            key = s1.split("=", 2)[1];
                            continue;
                        }
                        if (s1.contains("IV"))
                            iv = s1.split("=", 2)[1];
                    }
                }
            }
            String relativeUrl = url.substring(0, url.lastIndexOf("/") + 1);
            //将ts片段链接加入set集合
            for (int i = 0; i < split.length; i++) {
                String s = split[i];
                if (s.contains("#EXTINF")) {
                    String s1 = split[++i];
                    queue.put(StringUtils.isUrl(s1) ? s1 : mergeUrl(relativeUrl, s1));
                }
            }
            if (!StringUtils.isEmpty(key)) {
                key = key.replace("\"", "");
                return HttpUtil.downloadString(StringUtils.isUrl(key) ? key : mergeUrl(relativeUrl, key), Charset.defaultCharset()).replaceAll("\\s+", "");
            }
            return null;
        }

        public static String mergeUrl (String start, String end){
            if (end.startsWith("/"))
                end = end.replaceFirst("/", "");
            int position = 0;
            String subEnd, tempEnd = end;
            while ((position = end.indexOf("/", position)) != -1) {
                subEnd = end.substring(0, position + 1);
                if (start.endsWith(subEnd)) {
                    tempEnd = end.replaceFirst(subEnd, "");
                    break;
                }
                ++position;
            }
            return start + tempEnd;
    }
}
