package com.zzzzzzzs.syncfile;

import cn.hutool.core.io.FileUtil;
import cn.hutool.core.io.watch.SimpleWatcher;
import cn.hutool.core.io.watch.WatchMonitor;
import cn.hutool.core.lang.Console;
import cn.hutool.extra.ssh.JschUtil;
import com.jcraft.jsch.ChannelSftp;
import com.jcraft.jsch.Session;
import com.jcraft.jsch.SftpException;

import java.io.File;
import java.nio.file.Path;
import java.nio.file.WatchEvent;

public class SyncFile {
  public static void main(String[] args) {
    File file = FileUtil.file("C:\\Users\\simeitol\\Desktop\\1.txt");
    Session session = JschUtil.getSession("106.14.150.229", 22, "root", "Hminde1314");
    ChannelSftp channelSftp = JschUtil.openSftp(session, 5000);
    WatchMonitor.createAll(
            file,
            new SimpleWatcher() {
              @Override
              public void onModify(WatchEvent<?> event, Path currentPath) {
                try {
                  channelSftp.put(file.getAbsolutePath(), "/root/test/1.txt");
                  System.out.println("上传成功");
                } catch (SftpException e) {
                  e.printStackTrace();
                }
              }
            })
        .start();
  }
}
