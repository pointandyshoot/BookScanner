package au.id.pointandyshoot.bookscanner.core;

import java.text.Normalizer;
import java.util.*;

/** Matches one spatial OCR region at a time; never joins text across a shelf. */
public final class Matcher {
    public static final class Match {
        public final WantedBook book;
        public final double score;
        public final String reason;
        public Match(WantedBook book, double score, String reason) {
            this.book = book; this.score = score; this.reason = reason;
        }
    }
    public static String normalise(String s) {
        return Normalizer.normalize(s, Normalizer.Form.NFD).replaceAll("\\p{M}+", "")
                .toLowerCase(Locale.ROOT).replaceAll("[^\\p{L}\\p{N}]+", " ").trim().replaceAll("\\s+", " ");
    }
    public List<Match> find(String text, List<WantedBook> books) {
        String observed = normalise(text);
        if (observed.isEmpty()) return List.of();
        List<Match> found = new ArrayList<>();
        for (WantedBook b : books) {
            if (!b.enabled) continue;
            double author = b.author.isBlank() ? 0 : similarity(b.author, observed);
            if (b.title.equals("*")) {
                if (author >= .89) found.add(new Match(b, author, "Author match"));
                continue;
            }
            double title = similarity(b.title, observed);
            for (String alias : b.aliases) title = Math.max(title, similarity(alias, observed));
            if (title >= .86) found.add(new Match(b, title, "Title match"));
            else if (title >= .62 && author >= .89)
                found.add(new Match(b, .86, "Author + partial title"));
            else if (author >= .94)
                found.add(new Match(b, .70, "Author only — check title"));
        }
        found.sort(Comparator.comparingDouble((Match m) -> m.score).reversed());
        return found;
    }
    static double similarity(String target, String observed) {
        if (target.contains("*") || target.contains("?")) return glob(target, observed) ? .96 : 0;
        String t = normalise(target);
        if (t.isEmpty()) return 0;
        if ((" " + observed + " ").contains(" " + t + " ")) return 1;
        // Short titles/names are too easy to confuse. Require a whole-word exact match.
        if (t.length() < 6) return 0;
        String[] words = observed.split(" ");
        int count = t.split(" ").length;
        double best = 0;
        for (int start = 0; start < words.length; start++) {
            for (int n = Math.max(1, count - 1); n <= count + 1 && start + n <= words.length; n++) {
                String w = String.join(" ", Arrays.copyOfRange(words, start, start + n));
                best = Math.max(best, 1.0 - (double) distance(t, w) / Math.max(t.length(), w.length()));
            }
        }
        return best;
    }
    static boolean glob(String target, String observed) {
        // O(pattern length * observed length), even for adversarial imported globs.
        String normal = Normalizer.normalize(target, Normalizer.Form.NFD).replaceAll("\\p{M}+", "")
                .toLowerCase(Locale.ROOT).replaceAll("[^\\p{L}\\p{N}*?]+", " ").trim();
        if (normal.replace("*", "").replace("?", "").isBlank()) return false;
        boolean[] previous = new boolean[observed.length()+1];
        for (int j=0;j<previous.length;j++) previous[j]=j==0 || observed.charAt(j-1)==' ';
        for (char c : normal.toCharArray()) {
            boolean[] current = new boolean[previous.length];
            current[0] = c=='*' && previous[0];
            for (int j=1;j<current.length;j++) current[j] = c=='*'
                    ? previous[j] || current[j-1]
                    : previous[j-1] && (c=='?' || c==observed.charAt(j-1));
            previous=current;
        }
        for (int j=0;j<previous.length;j++)
            if(previous[j] && (j==observed.length() || observed.charAt(j)==' '))return true;
        return false;
    }
    static int distance(String a, String b) {
        int[] row = new int[b.length() + 1];
        for (int j = 0; j <= b.length(); j++) row[j] = j;
        for (int i = 1; i <= a.length(); i++) {
            int prev = row[0]; row[0] = i;
            for (int j = 1; j <= b.length(); j++) {
                int old = row[j];
                row[j] = Math.min(Math.min(row[j] + 1, row[j - 1] + 1), prev + (a.charAt(i - 1) == b.charAt(j - 1) ? 0 : 1));
                prev = old;
            }
        }
        return row[b.length()];
    }
}
