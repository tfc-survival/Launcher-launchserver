package launchserver.auth.provider;

import com.eclipsesource.json.Json;
import com.eclipsesource.json.JsonObject;
import com.eclipsesource.json.JsonValue;
import launcher.helper.IOHelper;
import launcher.helper.SecurityHelper;
import launcher.helper.VerifyHelper;
import launcher.serialize.config.entry.BlockConfigEntry;
import launcher.serialize.config.entry.StringConfigEntry;

import java.io.IOException;
import java.io.InputStreamReader;
import java.io.OutputStreamWriter;
import java.net.HttpURLConnection;
import java.net.URL;
import java.nio.charset.StandardCharsets;

public final class JsonAuthProvider extends AuthProvider {
    private static final int TIMEOUT = Integer.parseInt(
            System.getProperty("launcher.connection.timeout", Integer.toString(1500)));

    private final URL url;
    private final String userKeyName;
    private final String passKeyName;
    private final String ipKeyName;
    private final String responseUserKeyName;
    private final String responseErrorKeyName;

    JsonAuthProvider(BlockConfigEntry block) {
        super(block);
        String configUrl = block.getEntryValue("url", StringConfigEntry.class);
        userKeyName = VerifyHelper.verify_1(block.getEntryValue("userKeyName", StringConfigEntry.class), VerifyHelper.NOT_EMPTY, "Username key name can't be empty");
        passKeyName = VerifyHelper.verify_1(block.getEntryValue("passKeyName", StringConfigEntry.class), VerifyHelper.NOT_EMPTY, "Password key name can't be empty");
        ipKeyName = VerifyHelper.verify_1(block.getEntryValue("ipKeyName", StringConfigEntry.class), VerifyHelper.NOT_EMPTY, "IP key name can't be empty");
        responseUserKeyName = VerifyHelper.verify_1(block.getEntryValue("responseUserKeyName", StringConfigEntry.class), VerifyHelper.NOT_EMPTY, "Response username key can't be empty");
        responseErrorKeyName = VerifyHelper.verify_1(block.getEntryValue("responseErrorKeyName", StringConfigEntry.class), VerifyHelper.NOT_EMPTY, "Response error key can't be empty");
        url = IOHelper.convertToURL(configUrl);
    }

    @Override
    public AuthProviderResult auth(String login, String password, String ip) throws IOException {
        JsonObject request = Json.object().add(userKeyName, login).add(passKeyName, password).add(ipKeyName, ip);
        HttpURLConnection connection = (HttpURLConnection) url.openConnection();
        connection.setDoInput(true);
        connection.setDoOutput(true);
        connection.setRequestMethod("POST");
        connection.setRequestProperty("Content-Type", "application/json; charset=UTF-8");
        connection.setRequestProperty("Accept", "application/json");
        if (TIMEOUT > 0) {
            connection.setConnectTimeout(TIMEOUT);
        }

        OutputStreamWriter writer = new OutputStreamWriter(connection.getOutputStream(), StandardCharsets.UTF_8);
        writer.write(request.toString());
        writer.flush();
        writer.close();

        InputStreamReader reader;
        int statusCode = connection.getResponseCode();

        // Don't throw an exception when gets 4xx/5xx response
        // https://github.com/new-sashok724/Launcher/pull/54/commits/d3be2e243cf5476af000fd8850da9436a227eb2a
        if (200 <= statusCode && statusCode < 300) {
            reader = new InputStreamReader(connection.getInputStream(), StandardCharsets.UTF_8);
        } else {
            reader = new InputStreamReader(connection.getErrorStream(), StandardCharsets.UTF_8);
        }

        JsonValue content = Json.parse(reader);
        if (!content.isObject()) {
            return authError("Authentication server response is malformed");
        }

        JsonObject response = content.asObject();
        String value;

        if ((value = response.getString(responseUserKeyName, null)) != null) {
            return new AuthProviderResult(value, SecurityHelper.randomStringToken());
        } else if ((value = response.getString(responseErrorKeyName, null)) != null) {
            return authError(value);
        } else {
            return authError("Authentication server response is malformed");
        }
    }

    @Override
    public void close() {
        // pass
    }
}
