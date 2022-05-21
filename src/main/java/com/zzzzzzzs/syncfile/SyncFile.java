package com.zzzzzzzs.syncfile;

import cn.hutool.core.bean.BeanUtil;
import cn.hutool.core.io.FileUtil;
import cn.hutool.core.io.watch.SimpleWatcher;
import cn.hutool.core.io.watch.WatchMonitor;
import cn.hutool.extra.ssh.JschUtil;
import com.jcraft.jsch.ChannelSftp;
import com.jcraft.jsch.Session;
import com.jcraft.jsch.SftpException;
import org.yaml.snakeyaml.Yaml;

import java.io.File;
import java.io.FileInputStream;
import java.io.FileNotFoundException;
import java.io.InputStream;
import java.nio.file.Path;
import java.nio.file.WatchEvent;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.concurrent.atomic.AtomicReference;

public class SyncFile {
  public static void main(String[] args) throws FileNotFoundException {
    if (args.length < 1) {
      new IllegalArgumentException("请指定工作空间");
    }
    Yaml yaml = new Yaml();
    Map<String, Object> config;
    if (args.length < 2) {
      InputStream confIS = SyncFile.class.getClassLoader().getResourceAsStream("conf/config.yml");
      config = yaml.load(confIS);
    } else {
      //      System.out.println(System.getProperty("user.dir") + args[1]);
      // 读取文件
      File file = new File(System.getProperty("user.dir") + args[0]);
      System.out.println(file);
      InputStream confIS = new FileInputStream(file);
      config = yaml.load(confIS);
    }
    LinkedHashMap<String, Object> workspace = (LinkedHashMap) config.get("workspace");
    AtomicReference<Config> workspaceConf = new AtomicReference<>();
    workspace.forEach(
        (k, v) -> {
          if (args[0].equals(k)) {
            workspaceConf.set(BeanUtil.fillBeanWithMap((Map) v, new Config(), true));
            return;
          }
        });
    File file = FileUtil.file(workspaceConf.get().getMonitorFile());
    Session session =
        JschUtil.getSession(
            workspaceConf.get().getIp(),
            workspaceConf.get().getPort(),
            workspaceConf.get().getUser(),
            workspaceConf.get().getPassword());
    ChannelSftp channelSftp = JschUtil.openSftp(session, 5000);
    System.out.println("start watch");
    WatchMonitor.createAll(
            file,
            new SimpleWatcher() {
              @Override
              public void onModify(WatchEvent<?> event, Path currentPath) {
                try {
                  channelSftp.put(
                      file.getAbsolutePath(), workspaceConf.get().getUploadPath() + file.getName());
                  System.out.println("sync success");
                } catch (SftpException e) {
                  e.printStackTrace();
                }
              }
            })
        .start();
  }
}
