package fatall.reactor.osm;

import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.mockito.Mock;
import org.reactivestreams.Publisher;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.ApplicationContext;
import org.springframework.http.HttpStatus;
import org.springframework.test.context.ContextConfiguration;
import org.springframework.test.context.junit4.SpringJUnit4ClassRunner;
import org.springframework.test.util.ReflectionTestUtils;
import org.springframework.test.web.reactive.server.EntityExchangeResult;
import org.springframework.test.web.reactive.server.WebTestClient;
import org.springframework.web.reactive.function.server.ServerResponse;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;
import reactor.core.scheduler.Schedulers;

import java.io.File;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.nio.file.StandardCopyOption;
import java.time.Duration;
import java.util.Comparator;
import java.util.List;
import java.util.Random;
import java.util.function.Function;

import static fatall.reactor.osm.OsmApplication.TILES_PATH;
import static org.springframework.test.util.AssertionErrors.assertEquals;

@RunWith(SpringJUnit4ClassRunner.class)
@ContextConfiguration(classes = WebFluxConfig.class)
public class OsmApplicationTests {

    @Autowired
    private ApplicationContext context;
    private WebTestClient client;

    @Mock
    private TileHandler tileHandler;

    @Before
    public void setUp() {
        client = WebTestClient.bindToApplicationContext(context).build();
        tileHandler = new TileHandler();

        try {
            Files.walk(Paths.get(TILES_PATH))
                    .sorted(Comparator.reverseOrder())
                    .map(Path::toFile)
                    .forEach(File::delete);

        } catch (IOException e) {
            e.printStackTrace();
        }
    }

    @Test
    public void simpleLoadTile() throws IOException {
        EntityExchangeResult<byte[]> result = client.get()
                .uri("/3/4/1.png")
                .exchange()
                .expectStatus().isOk()
                .expectBody(byte[].class)
                .returnResult();

        assertEquals("Wrong Tile!", Files.readAllBytes(Paths.get(TILES_PATH + "/3/4/1.png")), result.getResponseBody());
    }

    @Test
    public void loadFromCache() throws IOException {
        Path path = Paths.get(TILES_PATH + "/1/1/1.png");
        Files.createDirectories(path.getParent());
        Files.copy(getClass().getResourceAsStream("/static/test.png"),
                Paths.get(TILES_PATH + "/1/1/1.png"), StandardCopyOption.REPLACE_EXISTING);

        Long count = Flux.range(1, 1000).parallel(6).runOn(Schedulers.parallel())
                .flatMap((Function<Integer, Publisher<ServerResponse>>) integer ->
                        (Mono<ServerResponse>) ReflectionTestUtils.invokeMethod(tileHandler, "getTileFromCache", "/1/1/1.png"))
                .filter(serverResponse -> serverResponse.statusCode().equals(HttpStatus.OK))
                .sequential().count().block();

        assertEquals("Wrong response count!", 1000L, count);
    }

    @Test
    public void simultaneouslyLoadOneTileFromWeb() {
        Long count = Flux.range(1, 12).parallel(12).runOn(Schedulers.parallel())
                .flatMap((Function<Integer, Publisher<ServerResponse>>) integer ->
                        (Mono<ServerResponse>) ReflectionTestUtils.invokeMethod(tileHandler, "getTileFromWeb", "/12/1/1.png"))
                .filter(serverResponse -> serverResponse.statusCode().equals(HttpStatus.OK))
                .sequential().count().block();

        assertEquals("Wrong response count!", 12L, count);
    }

    @Test
    public void highLoad() {
        final Random random = new Random();
        Long count = Flux.range(1, 10000)
                .buffer(12)
                .delayElements(Duration.ofMillis(1)) // we want to use cache
                .flatMap((Function<List<Integer>, Publisher<Integer>>) Flux::fromIterable)
                .parallel(12).runOn(Schedulers.parallel())
                .flatMap((Function<Integer, Publisher<ServerResponse>>) integer ->
                        (Mono<ServerResponse>) ReflectionTestUtils.invokeMethod(tileHandler, "getTile",
                                String.format("/4/%d/%d.png", random.nextInt(15), random.nextInt(15))))
                .filter(serverResponse -> serverResponse.statusCode().equals(HttpStatus.OK))
                .sequential().count().block();

        assertEquals("Wrong response count!", 10000L, count);
        assertEquals("Requests still remaining!", 0, ReflectionTestUtils.invokeMethod(tileHandler, "getActiveRequestsCount"));
    }

}
