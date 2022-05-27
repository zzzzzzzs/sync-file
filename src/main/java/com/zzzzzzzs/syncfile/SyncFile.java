package com.zzzzzzzs.syncfile;

import cn.hutool.core.bean.BeanUtil;
import cn.hutool.core.io.FileUtil;
import cn.hutool.core.io.watch.SimpleWatcher;
import cn.hutool.core.io.watch.WatchMonitor;
import cn.hutool.core.io.watch.watchers.DelayWatcher;
import com.jcraft.jsch.JSchException;
import com.jcraft.jsch.SftpException;
import org.yaml.snakeyaml.Yaml;

import java.io.File;
import java.io.FileInputStream;
import java.io.FileNotFoundException;
import java.io.InputStream;
import java.nio.file.Path;
import java.nio.file.WatchEvent;
import java.util.*;

// TODO 在 SyncFile 中的 WatchMonitor 调用 SSHUtil 中的 shellCmd 方法，InputStream 不起作用
public class SyncFile {

  public static void execCmd(List<String> config, SSHUtil ssh, String name) {
    // 黄色输出
    System.out.println("\033[33m" + "\n" + name + "\033[0m");
    Optional.ofNullable(config).orElse(new ArrayList<>()).stream()
        .filter(cmd -> null != cmd)
        .forEach(cmd -> ssh.shellCmd(cmd));
  }

  public static void main(String[] args) throws FileNotFoundException {
    if (args.length < 1) {
      throw new IllegalArgumentException("请指定工作空间");
    }
    Yaml yaml = new Yaml();
    Map<String, Object> config;
    if (args.length < 2) {
      InputStream confIS = SyncFile.class.getClassLoader().getResourceAsStream("conf/config.yml");
      config = yaml.load(confIS);
    } else {
      // 读取文件
      File file =
          new File(
              FileUtil.file(System.getProperty("user.dir")).getParent() + File.separator + args[1]);
      InputStream confIS = new FileInputStream(file);
      config = yaml.load(confIS);
    }
    LinkedHashMap<String, Object> workspace = (LinkedHashMap) config.get("workspace");
    final Config workspaceConf = new Config();
    for (Map.Entry<String, Object> entry : workspace.entrySet()) {
      String k = entry.getKey();
      Object v = entry.getValue();
      if (args[0].equals(k)) {
        BeanUtil.fillBeanWithMap((Map) v, workspaceConf, true);
        break;
      }
    }
    if (!workspaceConf.getUploadPath().endsWith("/")) {
      throw new IllegalArgumentException("上传路径必须以 / 结尾");
    }
    File file = FileUtil.file(workspaceConf.getMonitorFile());
    SSHUtil ssh =
        new SSHUtil(
            workspaceConf.getUser(),
            workspaceConf.getIp(),
            workspaceConf.getPort(),
            workspaceConf.getPassword());
    ssh.connect();
    // start cmd
    execCmd(workspaceConf.getStartCmd(), ssh, "start cmd");

    System.out.println("初始化上传文件");
    List<File> files = FileUtil.loopFiles(workspaceConf.getMonitorFile());
    files.forEach(
        ele -> {
          try {
            String src;
            String dst;
            if (file.isFile()) {
              src = workspaceConf.getMonitorFile();
              dst = workspaceConf.getUploadPath();
            } else {
              src = ele.getAbsolutePath();
              dst =
                  workspaceConf.getUploadPath()
                      + ele.getParent().replace(file.getParent(), "").replace("\\", "/");
            }
            File srcFile = new File(src);
            InputStream is = new FileInputStream(srcFile);
            ssh.uploadMore(dst, ele.getName(), is);
          } catch (FileNotFoundException e) {
            e.printStackTrace();
          } catch (SftpException e) {
            e.printStackTrace();
          }
        });
    System.out.println("初始化上传文件完成");
    System.out.println("start watch");

    WatchMonitor.createAll(
            file,
            new DelayWatcher(
                new SimpleWatcher() {
                  @Override
                  public void onModify(WatchEvent<?> event, Path currentPath) {
                    try {
                      // front cmd
                      execCmd(workspaceConf.getFrontCmd(), ssh, "front cmd");
                      // sync
                      String src;
                      String dst;
                      if (file.isFile()) {
                        src = workspaceConf.getMonitorFile();
                        dst = workspaceConf.getUploadPath();
                      } else {
                        src = currentPath + File.separator + event.context();
                        dst =
                            workspaceConf.getUploadPath()
                                + currentPath
                                    .toString()
                                    .replace(file.getParent(), "")
                                    .replace("\\", "/");
                      }
                      File srcFile = new File(src);
                      InputStream is = new FileInputStream(srcFile);
                      ssh.uploadMore(dst, event.context().toString(), is);
                      System.out.println("上传：" + dst + "/" + event.context());
                      System.out.println("\033[32m" + "sync success" + "\033[0m");
                      // back cmd
                      execCmd(workspaceConf.getBackCmd(), ssh, "back cmd");
                    } catch (SftpException e) {
                      e.printStackTrace();
                    } catch (FileNotFoundException e) {
                      e.printStackTrace();
                    }
                  }
                },
                1000))
        .setMaxDepth(Integer.MAX_VALUE)
        .start();
  }
}
