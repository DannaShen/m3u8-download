package com.dandan.project.main;


import cn.hutool.core.date.DateUtil;
import cn.hutool.core.io.FileUtil;
import cn.hutool.core.io.IoUtil;
import cn.hutool.core.thread.ThreadUtil;
import cn.hutool.core.util.URLUtil;
import cn.hutool.log.Log;
import cn.hutool.log.LogFactory;
import com.iheartradio.m3u8.*;
import com.iheartradio.m3u8.data.MediaPlaylist;
import com.iheartradio.m3u8.data.Playlist;
import com.iheartradio.m3u8.data.TrackData;

import java.io.*;
import java.net.URL;
import java.util.List;
import java.util.concurrent.*;
import java.util.concurrent.atomic.AtomicLong;
import java.util.concurrent.locks.ReadWriteLock;
import java.util.concurrent.locks.ReentrantReadWriteLock;

public class Test2 {


    public static final ReadWriteLock readWriteLock = new ReentrantReadWriteLock(true);
    private static final String DIR = "/Users/baoyongtao/m3u8";
    private static final String M3URL = "https://cctvbsh5c.v.live.baishancdnx.cn/live/cdrmcctv2_1_1800.m3u8";
    public static BlockingQueue<TrackData> tracksList = new LinkedBlockingDeque<>(1000);
    public static Log log = LogFactory.get(Test2.class);
    public static  ExecutorService executorService = ThreadUtil.newExecutor(4);

    public static void main(String[] args) {

        ExecutorService executorService = ThreadUtil.newSingleExecutor();
        executorService.submit(() -> {
            while (true) {
                InputStream inputStream = URLUtil.getStream(URLUtil.url(M3URL));
                PlaylistParser parser = new PlaylistParser(inputStream, Format.EXT_M3U, Encoding.UTF_8);
                try {
                    AtomicLong atomicInteger = new AtomicLong(0);
                    Playlist playlist = parser.parse();
                    if (playlist.hasMediaPlaylist()) {
                        MediaPlaylist mediaPlaylist = playlist.getMediaPlaylist();
                        List<TrackData> tracks = mediaPlaylist.getTracks();
                        for (TrackData track : tracks) {
                            log.info("完成解析结果:[{}]", track.getUri());
                            atomicInteger.addAndGet(Float.valueOf(track.getTrackInfo().duration).longValue());
                            tracksList.put(track);
                        }
                        writeFile(tracksList);
                        System.out.println("time:" + atomicInteger.get() + "，线程暂停开始。。。。。");
                        ThreadUtil.sleep(atomicInteger.get() * 1000);
                    }
                } catch (IOException | ParseException | PlaylistException e) {
                    e.printStackTrace();
                }
            }
        });


        ScheduledThreadPoolExecutor scheduledThreadPoolExecutor = new ScheduledThreadPoolExecutor(1);
        scheduledThreadPoolExecutor.scheduleAtFixedRate(() -> {
            List<File> fileList = FileUtil.loopFiles(DIR, name -> name.getName().endsWith(".ts"));
            OutputStream fileOutputStream = null;
            File file1 = new File(DIR + File.separatorChar + DateUtil.currentSeconds() + ".mp4");
            try {
                fileOutputStream = new FileOutputStream(file1);
                for (File file : fileList) {
                    IoUtil.read(FileUtil.getInputStream(file)).writeTo(fileOutputStream);
                    fileOutputStream.flush();
                }
                fileOutputStream.close();
            } catch (Exception e) {
                e.printStackTrace();
            } finally {
                if (FileUtil.size(file1) > 0) {
                    fileList.forEach(FileUtil::del);
                }
            }
        }, 0, 1, TimeUnit.HOURS);

    }

    private static void writeFile(BlockingQueue<TrackData> tracksList) {
        while (!tracksList.isEmpty()) {
            TrackData poll = tracksList.poll();
            String uri = poll.getUri();
            executorService.execute(() -> {
                log.info("开始下载：[{}]", uri);
                FileUtil.writeFromStream(URLUtil.getStream(URLUtil.url(uri)), new File(DIR + File.separator + FileUtil.getName(uri)));
                log.info("下载：[{}] 完成！！！", uri);
            });
        }
    }
}
