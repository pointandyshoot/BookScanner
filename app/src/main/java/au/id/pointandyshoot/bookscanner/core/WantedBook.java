package au.id.pointandyshoot.bookscanner.core;

import java.util.List;
import java.util.UUID;

/** A title/series glob, or an author wildcard when title is '*'. */
public final class WantedBook {
    public final String id, title, author;
    public final List<String> aliases;
    public final boolean enabled;

    public WantedBook(String id, String title, String author, List<String> aliases, boolean enabled) {
        this.id = id == null || id.isBlank() ? UUID.randomUUID().toString() : id;
        this.title = title.trim().isEmpty() ? "*" : title.trim();
        this.author = author.trim();
        this.aliases = List.copyOf(aliases);
        this.enabled = enabled;
        if (this.title.length() > 240 || this.author.length() > 160 || aliases.size() > 20
                || aliases.stream().anyMatch(a -> a.isBlank() || a.length() > 240))
            throw new IllegalArgumentException("Keep titles under 240 characters, authors under 160 and aliases under 20.");
        if (Matcher.normalise(this.title.replace("*", "").replace("?", "")).isBlank()
                && Matcher.normalise(this.author).isBlank())
            throw new IllegalArgumentException("Enter a title or an author.");
    }

    public String label() {
        return title.equals("*") ? "Any book by " + author : title;
    }
}
