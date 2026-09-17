package fr.mathildeuh.youneedme.expansion;

import java.util.List;

/**
 * Metadata read from an expansion jar's {@code expansion.yml} (the expansion equivalent of
 * {@code plugin.yml}):
 *
 * <pre>{@code
 * id: my-expansion
 * version: 1.0.0
 * main: com.example.myexpansion.MyExpansion
 * api-version: "1.0"
 * authors: [SomeDev]
 * description: What this expansion adds.
 * }</pre>
 */
public record ExpansionDescription(
        String id, String version, String main, String apiVersion, List<String> authors, String description) {}
