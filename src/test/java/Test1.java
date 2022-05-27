import cn.hutool.core.io.FileUtil;

import java.io.File;

public class Test1 {

  public static void main(String[] args) {
    File file = FileUtil.file("C:\\Users\\simeitol\\Desktop\\11");
    System.out.println(file.getName());
  }
}
