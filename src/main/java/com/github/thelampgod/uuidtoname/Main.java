package com.github.thelampgod.uuidtoname;

import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import org.apache.commons.csv.CSVFormat;
import org.apache.commons.csv.CSVParser;
import org.apache.commons.csv.CSVRecord;

import javax.net.ssl.HttpsURLConnection;
import java.io.*;
import java.net.URL;
import java.util.Map;
import java.util.Scanner;
import java.util.concurrent.ConcurrentHashMap;

import static com.google.gson.JsonParser.parseString;

public class Main {

    private static final Map<String, String> uuidCache = new ConcurrentHashMap<>();

    public static void main(String... args) throws IOException {

        if (args.length == 0) {
            System.out.println("Please specify input file.");
            return;
        }

        File file = new File(args[0]);
        File output = new File("./output.txt");

        if (args.length > 1) {
            output = new File(args[1]);
        }

        FileWriter fw;

        if (output.exists()) {
            System.out.println(output + " already exists! Delete before running UUIDtoName.");
            return;
        } else {
            fw = new FileWriter(output);
        }

        InputStreamReader input = new InputStreamReader(new FileInputStream(file));
        CSVParser csvParser = CSVFormat.EXCEL.withFirstRecordAsHeader().parse(input);
        int count = 0;
        for (CSVRecord record : csvParser) {

            JsonElement element = parseString(record.get("nbt"));
            JsonObject obj = element.getAsJsonObject();

            if (obj.get("OwnerUUID") != null && !obj.get("OwnerUUID").getAsString().isEmpty()) {
                ++count;
                String ownerUUID = obj.get("OwnerUUID").getAsString().replaceAll("-", "");

                String name = uuidCache.computeIfAbsent(ownerUUID, uuid -> {
                    try {
                        return lookupName(uuid);
                    } catch (FileNotFoundException e) {
                        return "unknown(uuid=" + uuid + ')';
                    } catch (Exception e) {
                        throw new RuntimeException("failed to fetch profile for " + uuid, e);
                    }
                });

                fw.write(record.get("id") + " " + obj.get("Pos") + " " + name + "\r\n");
                System.out.println(record.get("id") + " " + obj.get("Pos") + " " + name);

            } else if (obj.get("Tame") != null
                    && obj.get("Tame").getAsInt() == 1
                    && obj.get("OwnerUUID") == null
                    && !record.get("id").equals("minecraft:skeleton_horse")) {
                fw.write(record.get("id") + " " + obj.get("Pos") + " missing owneruuid\r\n");
                System.out.println(record.get("id") + " " + obj.get("Pos") + " missing owneruuid");
            }
        }

        fw.close();
        System.out.println("Found " + count + " tamed entities.");
    }

    private static String lookupName(String uuid) throws IOException {
        JsonElement element =
                getResources(
                        new URL(
                                "https://sessionserver.mojang.com/session/minecraft/profile/"
                                        + uuid)
                );

        return element.getAsJsonObject().get("name").getAsString();
    }

    private static JsonElement getResources(URL url)
            throws IOException {
        JsonElement data;
        HttpsURLConnection connection = null;
        try {
            connection = (HttpsURLConnection) url.openConnection();
            connection.setDoOutput(true);
            connection.setRequestMethod("GET");
            connection.setRequestProperty("Content-Type", "application/json");

            Scanner scanner = new Scanner(connection.getInputStream());
            StringBuilder builder = new StringBuilder();
            while (scanner.hasNextLine()) {
                builder.append(scanner.nextLine());
                builder.append('\n');
            }
            scanner.close();

            String json = builder.toString().trim();
            if (json.isEmpty())
                throw new FileNotFoundException();
            data = parseString(json);
        } finally {
            if (connection != null) {
                connection.disconnect();
            }
        }
        return data;
    }
}
