package com.zzzzzzzs.syncfile;

import cn.hutool.core.io.FileUtil;
import cn.hutool.core.io.watch.SimpleWatcher;
import cn.hutool.core.io.watch.WatchMonitor;
import com.jcraft.jsch.ChannelShell;
import com.jcraft.jsch.JSch;
import com.jcraft.jsch.Session;

import java.io.File;
import java.io.InputStream;
import java.io.OutputStream;
import java.nio.file.Path;
import java.nio.file.WatchEvent;
import java.util.Properties;
import java.util.concurrent.TimeUnit;

/**
 * @Description: ssh util
 */
public class SSHUtil {

  private String username;
  private String ip;
  private int port;
  private String password;

  private Session session;
  private ChannelShell channel;
  private InputStream in;
  private OutputStream os;

  public Session getSession() {
    return session;
  }

  public SSHUtil(String username, String ip, int port, String password) {
    this.username = username;
    this.ip = ip;
    this.port = port;
    this.password = password;
  }

  public void connect() {
    JSch ssh = new JSch();
    try {
      this.session = ssh.getSession(this.username, this.ip, this.port);
      this.session.setPassword(this.password);
      this.session.setServerAliveCountMax(0);
      Properties config = new Properties();
      config.put("StrictHostKeyChecking", "no");
      session.setConfig(config);
      this.session.connect(5000);
      channel = (ChannelShell) session.openChannel("shell");
      in = channel.getInputStream();
      channel.setPty(true);
      channel.connect();
      os = channel.getOutputStream();
      channel.setOutputStream(System.out);
      System.out.println("连接成功");
    } catch (Exception e) {
      throw new RuntimeException(e);
    }
  }

  public void shellCmd(String... cmds) {
    try {
      for (String cmd : cmds) {
        os.write((cmd + "\r\n").getBytes());
        os.flush();
        TimeUnit.MILLISECONDS.sleep(100);
      }
      // TODO 这里有问题
      //  使用 channel.setOutputStream(System.out); 可以暂时解决
      //      byte[] buffer = new byte[4096];
      //      while (in.available() > 0) {
      //        int len = in.read(buffer, 0, 4096);
      //        if (len < 0) break;
      //        System.out.println(new String(buffer, 0, len));
      //        System.out.println("结束执行命令");
      //      }
    } catch (Exception e) {
      throw new RuntimeException(e);
    }
  }
  // 测试
  public static void main(String[] args) throws Exception {
    SSHUtil ssh = new SSHUtil("root", "106.14.150.229", 22, "Hminde1314");
    ssh.connect();
    //    String[] cmds = new String[] {"cd /root/test", "netstat -nltp", "cat 1.txt"};
    //    ssh.shellCmd(cmds);
    //    ssh.shellCmd("ls", "pwd", "sudo su");
    //    for (int i = 0; i < 100; i++) {
    //      ssh.shellCmd("ls");
    //    }
    //    ssh.shellCmd("cd /root/test");
    //    List.of(1, 2, 3, 4, 5, 6, 7, 8, 9, 10).parallelStream()
    //        .forEach(
    //            i -> {
    //              System.out.println(Thread.currentThread().getId());
    //              ssh.shellCmd("ls");
    //            });
    //    System.out.println("执行完毕");
    File file = FileUtil.file("C:\\Users\\simeitol\\Desktop\\1.txt");
    WatchMonitor.createAll(
            file,
            new SimpleWatcher() {
              @Override
              public void onModify(WatchEvent<?> event, Path currentPath) {
                // front cmd
                //                  execCmd(workspaceConf.getFrontCmd(), ssh, "front cmd");
                System.out.println("aa" + Thread.currentThread().getId());
                ssh.shellCmd("ls");
                ssh.shellCmd("pwd");
                // sync
                System.out.println("\033[32m" + "sync success" + "\033[0m");
                // back cmd
                //                  execCmd(workspaceConf.getBackCmd(), ssh, "back cmd");
              }
            })
        .start();
  }
}
