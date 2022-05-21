import org.yaml.snakeyaml.Yaml;

import java.io.InputStream;
import java.util.Map;

public class Test {

  public static void main(String[] args) {
    Yaml yaml = new Yaml();
    InputStream input =
        new Test()
            .getClass()
            .getClassLoader()
            .getResourceAsStream("config/config.yml");
    Map<String, Object> map = yaml.load(input);
    System.out.println(map);
  }
}
