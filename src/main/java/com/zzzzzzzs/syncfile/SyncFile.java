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
import java.nio.charset.Charset;
import java.nio.file.Path;
import java.nio.file.WatchEvent;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.atomic.AtomicReference;

public class SyncFile {

  public static void execCmd(List<String> config, Session session, String name) {
    config.stream()
        .filter(cmd -> cmd != null)
        .forEach(
            cmd -> {
              String exec = JschUtil.exec(session, cmd, Charset.forName("UTF-8"));
              System.out.println(name + ":" + cmd + "#" + exec);
            });
  }

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
    final Config workspaceConf = new Config();
    // TODO
    for (Map.Entry<String, Object> entry : workspace.entrySet()) {
      String k = entry.getKey();
      Object v = entry.getValue();
      if (args[0].equals(k)) {
        BeanUtil.fillBeanWithMap((Map) v, workspaceConf, true);
        break;
      }
    }
    File file = FileUtil.file(workspaceConf.getMonitorFile());
    Session session =
        JschUtil.getSession(
            workspaceConf.getIp(),
            workspaceConf.getPort(),
            workspaceConf.getUser(),
            workspaceConf.getPassword());
    // start cmd
    execCmd(workspaceConf.getStartCmd(), session, "start cmd");

    ChannelSftp channelSftp = JschUtil.openSftp(session, 5000);
    System.out.println("start watch");
    WatchMonitor.createAll(
            file,
            new SimpleWatcher() {
              @Override
              public void onModify(WatchEvent<?> event, Path currentPath) {
                try {
                  // front cmd
                  execCmd(workspaceConf.getFrontCmd(), session, "front cmd");
                  channelSftp.put(
                      file.getAbsolutePath(), workspaceConf.getUploadPath() + file.getName());
                  System.out.println("\033[32m" + "sync success" + "\033[0m");
                  // back cmd
                  execCmd(workspaceConf.getBackCmd(), session, "back cmd");
                } catch (SftpException e) {
                  e.printStackTrace();
                }
              }
            })
        .start();
  }
}
