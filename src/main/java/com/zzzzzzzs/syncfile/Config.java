package com.zzzzzzzs.syncfile;

import lombok.Data;

import java.util.List;

@Data
public class Config {
  private String monitorFile;
  private String ip;
  private int port;
  private String user;
  private String password;
  private String uploadPath;
  private List<String> startCmd;
  private List<String> frontCmd;
  private List<String> backCmd;

}
