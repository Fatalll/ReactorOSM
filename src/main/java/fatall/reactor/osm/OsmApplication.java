package fatall.reactor.osm;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Paths;

@SpringBootApplication
public class OsmApplication {

    static final String TILES_PATH = "tiles";

    public static void main(String[] args) throws IOException {
        Files.createDirectories(Paths.get(TILES_PATH)).toAbsolutePath();
        SpringApplication.run(OsmApplication.class, args);
    }
}
