package launchserver.auth.provider;

import launcher.helper.VerifyHelper;
import launcher.serialize.config.entry.BlockConfigEntry;
import launcher.serialize.config.entry.StringConfigEntry;
import launchserver.auth.AuthException;

public final class RejectAuthProvider extends AuthProvider {
    private final String message;

    RejectAuthProvider(BlockConfigEntry block) {
        super(block);
        message = VerifyHelper.verify_1(block.getEntryValue("message", StringConfigEntry.class), VerifyHelper.NOT_EMPTY,
                "Auth error message can't be empty");
    }

    @Override
    public AuthProviderResult auth(String login, String password, String ip) throws AuthException {
        return authError(message);
    }

    @Override
    public void close() {
        // Do nothing
    }
}
