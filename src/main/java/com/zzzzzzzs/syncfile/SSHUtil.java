package com.zzzzzzzs.syncfile;

import cn.hutool.core.io.FileUtil;
import cn.hutool.core.io.watch.SimpleWatcher;
import cn.hutool.core.io.watch.WatchMonitor;
import com.jcraft.jsch.*;
import org.apache.commons.io.IOUtils;

import java.io.*;
import java.nio.file.Path;
import java.nio.file.WatchEvent;
import java.util.Properties;
import java.util.Vector;
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
  private ChannelSftp channelSftp;

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

      channelSftp = (ChannelSftp) session.openChannel("sftp");
      channelSftp.connect();

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

  /**
   * 递归根据路径创建文件夹
   *
   * @param dirs 根据 / 分隔后的数组文件夹名称
   * @param tempPath 拼接路径
   * @param length 文件夹的格式
   * @param index 数组下标
   * @return
   */
  public void mkdirDir(String[] dirs, String tempPath, int length, int index) {
    // 以"/a/b/c/d"为例按"/"分隔后,第0位是"";顾下标从1开始
    index++;
    if (index < length) {
      // 目录不存在，则创建文件夹
      tempPath += "/" + dirs[index];
    }
    try {
      System.out.println("检测目录[" + tempPath + "]");
      channelSftp.cd(tempPath);
      if (index < length) {
        mkdirDir(dirs, tempPath, length, index);
      }
    } catch (SftpException ex) {
      System.out.println("创建目录[" + tempPath + "]");
      try {
        channelSftp.mkdir(tempPath);
        channelSftp.cd(tempPath);
      } catch (SftpException e) {
        e.printStackTrace();
        System.err.println("创建目录[" + tempPath + "]失败,异常信息[" + e.getMessage() + "]");
      }
      System.out.println("进入目录[" + tempPath + "]");
      mkdirDir(dirs, tempPath, length, index);
    }
  }

  /**
   * 将输入流的数据上传到sftp作为文件（多层目录）
   *
   * @param directory 上传到该目录(多层目录)
   * @param sftpFileName sftp端文件名
   * @param input 输入流
   * @throws SftpException
   * @throws Exception
   */
  public void uploadMore(String directory, String sftpFileName, InputStream input)
      throws SftpException {
    try {
      channelSftp.cd(directory);
    } catch (SftpException e) {
      // 目录不存在，则创建文件夹
      String[] dirs = directory.split("/");
      String tempPath = "";
      int index = 0;
      mkdirDir(dirs, tempPath, dirs.length, index);
    }
    channelSftp.put(input, sftpFileName); // 上传文件
  }

  /**
   * 将输入流的数据上传到sftp作为文件
   *
   * @param directory 上传到该目录(单层目录)
   * @param sftpFileName sftp端文件名
   * @param input 输入流
   * @throws SftpException
   * @throws Exception
   */
  public void upload(String directory, String sftpFileName, InputStream input)
      throws SftpException {
    try {
      channelSftp.cd(directory);
    } catch (SftpException e) {
      System.err.println("directory is not exist");
      channelSftp.mkdir(directory);
      channelSftp.cd(directory);
    }
    channelSftp.put(input, sftpFileName);
    System.out.println(String.format("file: %s is upload successful", sftpFileName));
  }

  /**
   * 上传单个文件
   *
   * @param directory 上传到sftp目录
   * @param uploadFile 要上传的文件,包括路径
   * @throws FileNotFoundException
   * @throws SftpException
   * @throws Exception
   */
  public void upload(String directory, String uploadFile)
      throws FileNotFoundException, SftpException {
    File file = new File(uploadFile);
    upload(directory, file.getName(), new FileInputStream(file));
  }

  /**
   * 将byte[]上传到sftp，作为文件。注意:从String生成byte[]是，要指定字符集。
   *
   * @param directory 上传到sftp目录
   * @param sftpFileName 文件在sftp端的命名
   * @param byteArr 要上传的字节数组
   * @throws SftpException
   * @throws Exception
   */
  public void upload(String directory, String sftpFileName, byte[] byteArr) throws SftpException {
    upload(directory, sftpFileName, new ByteArrayInputStream(byteArr));
  }

  /**
   * 将字符串按照指定的字符编码上传到sftp
   *
   * @param directory 上传到sftp目录
   * @param sftpFileName 文件在sftp端的命名
   * @param dataStr 待上传的数据
   * @param charsetName sftp上的文件，按该字符编码保存
   * @throws UnsupportedEncodingException
   * @throws SftpException
   * @throws Exception
   */
  public void upload(String directory, String sftpFileName, String dataStr, String charsetName)
      throws UnsupportedEncodingException, SftpException {
    upload(directory, sftpFileName, new ByteArrayInputStream(dataStr.getBytes(charsetName)));
  }

  /**
   * 下载文件
   *
   * @param directory 下载目录
   * @param downloadFile 下载的文件
   * @param saveFile 存在本地的路径
   * @throws SftpException
   * @throws FileNotFoundException
   * @throws Exception
   */
  public void download(String directory, String downloadFile, String saveFile)
      throws SftpException, FileNotFoundException {
    if (directory != null && !"".equals(directory)) {
      channelSftp.cd(directory);
    }
    File file = new File(saveFile);
    channelSftp.get(downloadFile, new FileOutputStream(file));
    System.out.println(String.format("file:%s is download successful", downloadFile));
  }

  /**
   * 下载文件
   *
   * @param directory 下载目录
   * @param downloadFile 下载的文件名
   * @return 字节数组
   * @throws SftpException
   * @throws IOException
   * @throws Exception
   */
  public byte[] download(String directory, String downloadFile) throws SftpException, IOException {
    if (directory != null && !"".equals(directory)) {
      channelSftp.cd(directory);
    }
    InputStream is = channelSftp.get(downloadFile);
    byte[] fileData = IOUtils.toByteArray(is);
    System.out.println(String.format("file: %s is download successful", downloadFile));
    return fileData;
  }

  /**
   * 删除文件
   *
   * @param directory 要删除文件所在目录
   * @param deleteFile 要删除的文件
   * @throws SftpException
   * @throws Exception
   */
  public void delete(String directory, String deleteFile) throws SftpException {
    channelSftp.cd(directory);
    channelSftp.rm(deleteFile);
  }

  /**
   * 列出目录下的文件
   *
   * @param directory 要列出的目录
   * @return
   * @throws SftpException
   */
  public Vector<?> listFiles(String directory) throws SftpException {
    return channelSftp.ls(directory);
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
