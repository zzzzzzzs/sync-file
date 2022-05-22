package com.zzzzzzzs.syncfile;

import cn.hutool.core.bean.BeanUtil;
import cn.hutool.core.io.FileUtil;
import cn.hutool.core.io.watch.SimpleWatcher;
import cn.hutool.core.io.watch.WatchMonitor;
import cn.hutool.extra.ssh.JschUtil;
import com.jcraft.jsch.ChannelSftp;
import com.jcraft.jsch.SftpException;
import org.yaml.snakeyaml.Yaml;

import java.io.File;
import java.io.FileInputStream;
import java.io.FileNotFoundException;
import java.io.InputStream;
import java.nio.file.Path;
import java.nio.file.WatchEvent;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

// TODO 在 SyncFile 中的 WatchMonitor 调用 SSHUtil 中的 shellCmd 方法，InputStream 不起作用
public class SyncFile {

  public static void execCmd(List<String> config, SSHUtil ssh, String name) {
    synchronized (SSHUtil.class) {
      System.out.println("++++++++++++++++++++++++++++++");
      System.out.println(name + ":");
      config.stream().filter(cmd -> cmd != null).forEach(cmd -> ssh.shellCmd(cmd));
      System.out.println("------------------------------");
    }
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
    SSHUtil ssh =
        new SSHUtil(
            workspaceConf.getUser(),
            workspaceConf.getIp(),
            workspaceConf.getPort(),
            workspaceConf.getPassword());
    ssh.connect();
//    ssh.shellCmd("ls -lh");
    // start cmd
    execCmd(workspaceConf.getStartCmd(), ssh, "start cmd");

    ChannelSftp channelSftp = JschUtil.openSftp(ssh.getSession(), 5000);
    System.out.println("start watch");
    WatchMonitor.createAll(
            file,
            new SimpleWatcher() {
              @Override
              public void onModify(WatchEvent<?> event, Path currentPath) {
                synchronized (SSHUtil.class) {
                  try {
                    // front cmd
                    execCmd(workspaceConf.getFrontCmd(), ssh, "front cmd");
                    // sync
                    channelSftp.put(
                        file.getAbsolutePath(), workspaceConf.getUploadPath() + file.getName());
                    System.out.println("\033[32m" + "sync success" + "\033[0m");
                    // back cmd
                    execCmd(workspaceConf.getBackCmd(), ssh, "back cmd");
                  } catch (SftpException e) {
                    e.printStackTrace();
                  }
                }
              }
            })
        .start();
  }
}
