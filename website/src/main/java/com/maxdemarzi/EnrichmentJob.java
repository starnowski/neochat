package com.maxdemarzi;

import com.google.common.util.concurrent.AbstractScheduledService;
import okhttp3.MediaType;
import okhttp3.RequestBody;
import org.neo4j.driver.v1.Driver;
import retrofit2.Response;

import java.util.*;
import java.util.concurrent.LinkedBlockingQueue;
import java.util.concurrent.TimeUnit;

public class EnrichmentJob extends AbstractScheduledService {
    public LinkedBlockingQueue<HashMap<String, Object>> queue = new LinkedBlockingQueue<>();

    private static Driver driver;
    private static FullContactAPI fc;

    public EnrichmentJob(Driver driver, FullContactAPI fc) {
        if (!this.isRunning()){
            this.startAsync();
            this.awaitRunning();
        }
        this.driver = driver;
        this.fc = fc;
    }

    @Override
    protected void runOneIteration() throws Exception {
        List<HashMap<String, Object>> writes = new ArrayList<>();
        // Maximum of 60 per minute
        queue.drainTo(writes,1);
        if(!writes.isEmpty()) {
            HashMap<String, Object> map = writes.get(0);
            RequestBody body = RequestBody.create(
                    "{ \"email\":\"" + map.get("email") + "\" , \"phone\": \"" + map.get("phone") + "\"  }",
                    MediaType.parse("text/plain"));

            Response fcResponse = fc.enrich(body).execute();

            if (fcResponse.isSuccessful()) {
                Map<String, Object> properties = (Map<String, Object>) fcResponse.body();
                if (properties != null) {
                    properties.values().removeIf(Objects::isNull);
                    properties.remove("details");
                    properties.remove("dataAddOns");
                    // Only save valid names
                    String name = (String)properties.getOrDefault("fullName", "");
                    if (!name.isEmpty()) {
                        properties.put("name", name);
                    }
                }
                CypherQueries.EnrichMember(driver, (String)map.get("email"), (String)map.get("phone"), properties);
            }
        }


    }

    @Override
    protected Scheduler scheduler() {
        return Scheduler.newFixedRateSchedule(0, 1, TimeUnit.SECONDS);
    }
}
