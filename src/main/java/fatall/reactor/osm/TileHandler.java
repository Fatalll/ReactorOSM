package fatall.reactor.osm;

import org.jetbrains.annotations.NotNull;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Component;
import org.springframework.web.reactive.function.client.WebClient;
import org.springframework.web.reactive.function.server.ServerRequest;
import org.springframework.web.reactive.function.server.ServerResponse;
import reactor.core.publisher.Mono;
import reactor.core.scheduler.Schedulers;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.concurrent.ConcurrentHashMap;
import java.util.logging.Level;
import java.util.logging.Logger;

import static org.springframework.web.reactive.function.server.ServerResponse.ok;

@Component
public class TileHandler {
    // hashmap for preventing doubling identical queries
    private ConcurrentHashMap<String, Mono<ServerResponse>> requests = new ConcurrentHashMap<>();
    private WebClient client;

    public TileHandler() {
        client = WebClient.builder()
                .baseUrl("https://a.tile.openstreetmap.org")
                .defaultHeader(HttpHeaders.CONTENT_TYPE, "image/png")
                .build();
    }

    @NotNull
    Mono<ServerResponse> getTile(@NotNull ServerRequest request) {
        // parsing x, y coordinate and zoom for request
        String imagePath = String.format("/%s/%s/%s", request.pathVariable("z"),
                request.pathVariable("x"), request.pathVariable("y"));

        return getTile(imagePath);
    }

    @NotNull
    private Mono<ServerResponse> getTile(@NotNull String imagePath) {
        // probably this request is already executing
        if (requests.containsKey(imagePath)) {
            return requests.get(imagePath);
        }

        // first trying to get tile from cache, if not succeed load from web
        return getTileFromCache(imagePath).switchIfEmpty(Mono.defer(() -> getTileFromWeb(imagePath)));
    }

    @NotNull
    private Mono<ServerResponse> getTileFromCache(@NotNull String imagePath) {
        Path imageLocalPath = Paths.get(OsmApplication.TILES_PATH + imagePath);

        // if not exists load tile from server
        if (!Files.exists(imageLocalPath)) {
            return Mono.empty();
        }

        return ok().contentType(MediaType.IMAGE_PNG).body(
                Mono.fromCallable(() -> Files.readAllBytes(imageLocalPath)).subscribeOn(Schedulers.elastic()),
                byte[].class
        );
    }

    @NotNull
    private synchronized Mono<ServerResponse> getTileFromWeb(@NotNull String imagePath) {
        if (requests.containsKey(imagePath)) { // prevent double adding (duplication above because of synchronized)
            return requests.get(imagePath);
        }

        Mono<byte[]> body = client.get().uri(imagePath)
                .retrieve().bodyToFlux(byte[].class)
                .replay().autoConnect().next() // fix multiply requests
                .subscribeOn(Schedulers.elastic());

        // ignore error because of handling it in the next subscriber (disable warning)
        // save file to disk in another thread (i don't understand, why this warning here)
        body.onErrorResume(e -> Mono.empty()).subscribe(bytes -> {
            try {
                Path imageLocalPath = Paths.get(OsmApplication.TILES_PATH + imagePath);
                Files.createDirectories(imageLocalPath.getParent());
                Files.write(imageLocalPath, bytes);
            } catch (IOException e) {
                Logger.getLogger("fatall.reactor.osm.TileHandler").log(Level.SEVERE, "Failed to cache image to file! " + imagePath, e);
            }
        });

        Mono<ServerResponse> serverResponseMono = body
                .flatMap(bytes -> ok().contentType(MediaType.IMAGE_PNG).body(Mono.just(bytes), byte[].class))
                .onErrorResume(throwable -> ServerResponse.notFound().build()) // if not found, than 404 error
                .doFinally(signalType -> requests.remove(imagePath)); // remove request from active after finished

        // warning, but 100% guarantee that there is no this key in the map
        if (requests.put(imagePath, serverResponseMono) != null) {
            // in case of magic
            Logger.getLogger("fatall.reactor.osm.TileHandler").log(Level.SEVERE, "Replacing already existed request!");
        }

        return serverResponseMono;
    }

    private int getActiveRequestsCount() {
        return requests.size();
    }
}
