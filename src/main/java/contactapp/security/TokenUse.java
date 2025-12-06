package contactapp.security;

/**
 * Enumerates intended token usage modes.
 *
 * <p>SESSION tokens are bound to a browser session and must be accompanied by a
 * fingerprint cookie. API tokens are intended for programmatic clients and are
 * not bound to cookies.</p>
 */
public enum TokenUse {
    SESSION("session"),
    API("api");

    private final String claimValue;

    TokenUse(final String claimValue) {
        this.claimValue = claimValue;
    }

    public String claimValue() {
        return claimValue;
    }

    /**
     * Resolves a token use from the claim value.
     *
     * @param claim the claim value
     * @return the matching TokenUse, or null if unknown
     */
    public static TokenUse fromClaim(final String claim) {
        if (claim == null) {
            return null;
        }
        for (TokenUse use : values()) {
            if (use.claimValue.equals(claim)) {
                return use;
            }
        }
        return null;
    }
}
