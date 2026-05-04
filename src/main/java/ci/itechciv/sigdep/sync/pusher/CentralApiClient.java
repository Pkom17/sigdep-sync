package ci.itechciv.sigdep.sync.pusher;

import ci.itechciv.sigdep.contracts.EntityType;
import ci.itechciv.sigdep.contracts.SyncBatchRequest;
import ci.itechciv.sigdep.contracts.SyncBatchResponse;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.io.IOException;
import okhttp3.MediaType;
import okhttp3.OkHttpClient;
import okhttp3.Request;
import okhttp3.RequestBody;
import okhttp3.Response;
import org.springframework.stereotype.Component;

import ci.itechciv.sigdep.sync.config.SyncProperties;

@Component
public class CentralApiClient {

    private static final MediaType JSON = MediaType.parse("application/json");

    private final OkHttpClient http;
    private final ObjectMapper mapper;
    private final SyncProperties props;

    public CentralApiClient(OkHttpClient http, ObjectMapper mapper, SyncProperties props) {
        this.http = http;
        this.mapper = mapper;
        this.props = props;
    }

    public SyncBatchResponse push(EntityType entityType, SyncBatchRequest<?> batch, String bearerToken) throws IOException {
        String url = props.centralApiUrl() + "/api/v1/sync/" + entityType.name().toLowerCase();
        RequestBody body = RequestBody.create(mapper.writeValueAsBytes(batch), JSON);
        Request req = new Request.Builder()
                .url(url)
                .header("Authorization", "Bearer " + bearerToken)
                .post(body)
                .build();

        try (Response resp = http.newCall(req).execute()) {
            if (!resp.isSuccessful() || resp.body() == null) {
                throw new IOException("Central API returned HTTP " + resp.code());
            }
            return mapper.readValue(resp.body().bytes(), SyncBatchResponse.class);
        }
    }
}
