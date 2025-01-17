package launchserver.response.auth;

import launcher.client.ClientProfile;
import launcher.helper.IOHelper;
import launcher.helper.LogHelper;
import launcher.helper.SecurityHelper;
import launcher.helper.VerifyHelper;
import launcher.serialize.HInput;
import launcher.serialize.HOutput;
import launcher.serialize.signed.SignedObjectHolder;
import launchserver.HackHandler;
import launchserver.LaunchServer;
import launchserver.auth.AuthException;
import launchserver.auth.limiter.AuthLimiterHWIDConfig;
import launchserver.auth.limiter.AuthLimiterIPConfig;
import launchserver.auth.provider.AuthProvider;
import launchserver.auth.provider.AuthProviderResult;
import launchserver.helpers.ImmutableByteArray;
import launchserver.helpers.Pair;
import launchserver.response.Response;
import launchserver.response.profile.ProfileByUUIDResponse;

import javax.crypto.BadPaddingException;
import javax.crypto.IllegalBlockSizeException;
import java.sql.SQLException;
import java.util.Arrays;
import java.util.Collection;
import java.util.Map;
import java.util.UUID;
import java.util.stream.Collectors;

public final class AuthResponse extends Response {
    private final String ip;

    public AuthResponse(LaunchServer server, HInput input, HOutput output, String ip) {
        super(server, ip, input, output);
        this.ip = ip;
    }

    private static String echo(int length) {
        char[] chars = new char[length];
        Arrays.fill(chars, '*');
        return new String(chars);
    }

    @Override
    public void reply() throws Throwable {
        String login = input.readString(255);
        byte[] encryptedPassword = input.readByteArray(SecurityHelper.CRYPTO_MAX_LENGTH);
        byte[] hwid = input.readByteArray(SecurityHelper.HWID_MAX_LENGTH);
        boolean hacked = input.readBoolean();

        // Decrypt password
        String password;
        try {
            password = IOHelper.decode(SecurityHelper.newRSADecryptCipher(server.privateKey).
                    doFinal(encryptedPassword));
        } catch (IllegalBlockSizeException | BadPaddingException ignored) {
            requestError("Password decryption error");
            return;
        }

        // Authenticate
        debug("Login: '%s', Password: '%s'", login, echo(password.length()));
        AuthProviderResult result;
        try {
            // Лесенка чтоб ее
            if (server.config.authLimit) {
                if (AuthLimiterIPConfig.Instance.getBlockIp().stream().anyMatch(s -> s.equals(ip)) && server.config.authLimitConfig.useBlockIp) {
                    AuthProvider.authError(server.config.authLimitConfig.authBannedString);
                    return;
                }

                if (AuthLimiterIPConfig.Instance.getAllowIp().stream().noneMatch(s -> s.equals(ip))) {
                    if (server.config.authLimitConfig.onlyAllowIp) {
                        AuthProvider.authError(server.config.authLimitConfig.authNotWhitelistString);
                        return;
                    }

                    if (server.config.authLimitConfig.useAllowIp) {
                        if (server.limiter.isLimit(ip)) {
                            AuthProvider.authError(server.config.authLimitConfig.authRejectString);
                            return;
                        }
                    }
                }
            }

            result = server.config.authProvider.auth(login, password, ip);
            if (!VerifyHelper.isValidUsername(result.username)) {
                AuthProvider.authError(String.format("Illegal result: '%s'", result.username));
                return;
            }

            checkHWID(result.username, hwid);
            if (hacked) {
                System.out.println("violated client of player: " + result.username);
                HackHandler.instance.addNick(result.username);
                AuthProvider.authError(server.config.authLimitConfig.authBannedString);
            }

        } catch (AuthException e) {
            requestError(e.getMessage());
            return;
        } catch (Throwable exc) {
            LogHelper.error(exc);
            requestError("Internal auth provider error");
            return;
        }
        debug("Auth: '%s' -> '%s', '%s'", login, result.username, result.accessToken);

        // Authenticate on server (and get UUID)
        UUID uuid;
        try {
            uuid = server.config.authHandler.auth(result);
        } catch (AuthException e) {
            requestError(e.getMessage());
            return;
        } catch (Throwable exc) {
            LogHelper.error(exc);
            requestError("Internal auth handler error");
            return;
        }
        writeNoError(output);

        // Write profile and UUID
        ProfileByUUIDResponse.getProfile(server, uuid, result.username).write(output);
        output.writeInt(result.accessToken.length());
        output.writeASCII(result.accessToken, -result.accessToken.length());

        // Write clients profiles list
        Collection<SignedObjectHolder<ClientProfile>> profiles =
                server.getProfiles().stream()
                        .filter(profile -> profile.object.isWhitelisted(login))
                        .collect(Collectors.toList());
        output.writeLength(profiles.size(), 0);
        for (SignedObjectHolder<ClientProfile> profile : profiles) {
            profile.write(output);
        }
        output.flush();
    }

    private void checkHWID(String nickname, byte[] hwid) throws AuthException {
        ImmutableByteArray actualHWID = new ImmutableByteArray(hwid);
        try {
            AuthLimiterHWIDConfig hwidHandler = server.config.hwidHandler;
            Map<ImmutableByteArray, Boolean> knownHWID = hwidHandler.getHardware(nickname);
            boolean needInsert = !knownHWID.containsKey(actualHWID);
            boolean banned = knownHWID.values().stream().anyMatch(i -> i);
            if (needInsert) {
                Pair<Integer, Boolean> id_Banned = hwidHandler.getOrRegisterHWID(hwid, banned);
                hwidHandler.addHardwareToUser(nickname, id_Banned.left);
                banned = banned | id_Banned.right;
            }
            if (banned)
                AuthProvider.authError(server.config.authLimitConfig.authBannedString);
        } catch (SQLException e) {
            e.printStackTrace();
            AuthProvider.authError("Internal error");
        }
    }
}
