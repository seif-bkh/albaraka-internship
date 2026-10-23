package com.albaraka.backend.services;

import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Removes every trace of provenance from an answer <em>before a customer reads it</em>.
 *
 * <p>The assistant is built from bank documents and its structured output carries
 * citations, but the product decision is that customers never see them: the
 * answer must read as the bank's own words, and an internal file name or a
 * {@code [S3]} marker in a public chat is at best confusing and at worst a map of
 * what the retrieval layer holds. Provenance is not lost — it is persisted on the
 * exchange row and shown to reviewers in the admin console
 * ({@code /api/admin/monitoring/live-feed}), it is simply not part of the
 * customer-facing payload.
 *
 * <p>Sanitising here rather than in the browser matters: the answer is persisted
 * and relayed through several surfaces (floating widget, public chat page, portal
 * assistant, and any future embed), and a client-side filter can be bypassed by
 * reading the network response. The frontends keep a display-only tidy-up for
 * markdown, but they are no longer the guarantee.
 *
 * <p>Pure and static on purpose — no Spring context, no state on the shared path,
 * and the rules are unit-tested directly (see {@code AnswerSanitizerTests}).
 */
public final class AnswerSanitizer {

    /**
     * Bracketed citation markers: {@code [S1]}, {@code [S1, S2]}, {@code [doc_12]},
     * {@code [Source : x]}, {@code [réf. 3]}. The last shape is the one a model writes
     * when it points at a numbered bibliography entry, and it carries a number rather
     * than a colon — which is also what keeps it from eating an ordinary bracketed
     * aside: a bare {@code [2]} or {@code [voir note]} is prose, this is a reference.
     */
    private static final Pattern BRACKET_CITATION = Pattern.compile(
            "\\[\\s*(?:(?:S|SRC|DOC)[\\s_-]?\\d+(?:\\s*[,;]\\s*(?:S|SRC|DOC)?\\d+)*"
                    + "|doc_\\d+"
                    + "|(?:réf|ref|src|source|citation|المصدر|الموارد)\\s*\\.?\\s*\\d+"
                    + "|(?:sources?|références?|refs?|citations?|resources?|المصدر|المصادر|الموارد|الوثائق|مراجع)[ \\t]*[:：][^\\]]*)"
                    + "\\s*]",
            Pattern.CASE_INSENSITIVE | Pattern.UNICODE_CASE);

    /** Parenthesised mentions: {@code (Source : guide.pdf)}, {@code (المصدر: دورية)}, {@code (réf. 3)}. */
    private static final Pattern PARENTHETIC_CITATION = Pattern.compile(
            "[(\\[][ \\t]*(?:(?:sources?|références?|refs?|citations?|resources?|المصدر|المصادر|الموارد|الوثائق|مراجع)"
                    + "[ \\t]*[:：][^)\\]]*|(?:réf|ref|src|source|citation|المصدر|الموارد)\\s*\\.?\\s*\\d+)[)\\]]",
            Pattern.CASE_INSENSITIVE | Pattern.UNICODE_CASE);

    /** Markdown link whose label is itself a citation: dropped entirely, text and target. */
    private static final Pattern MARKDOWN_CITATION_LINK = Pattern.compile(
            "[ \\t]*\\[[^\\]]{0,80}(?:sources?|resources?|المصدر|المصادر|الموارد|الوثائق)[^\\]]{0,80}\\]"
                    + "\\((?:https?://|www\\.|/)[^)\\s]*\\)",
            Pattern.CASE_INSENSITIVE | Pattern.UNICODE_CASE);

    /** Any other markdown link: the label stays (it is prose), the target goes. */
    private static final Pattern MARKDOWN_LINK = Pattern.compile(
            "\\[([^\\]]{1,200})\\]\\((?:https?://|www\\.|/|mailto:)[^)\\s]*\\)");

    /**
     * Links and paths that only make sense inside our own infrastructure: they must
     * never reach a browser. Public https links to real sites are left alone.
     */
    private static final Pattern INTERNAL_LINK = Pattern.compile(
            "(?:https?://|ftp://)(?:localhost|127\\.|0\\.0\\.0\\.0|10\\.|192\\.168\\.|172\\.(?:1[6-9]|2\\d|3[01])\\."
                    + "|[\\w.-]*\\.(?:internal|local|corp|intranet)|minio|rustfs|backend|agent|postgres|redis)"
                    + "[^\\s)\\]]*",
            Pattern.CASE_INSENSITIVE);

    /**
     * Bare document references a model may paste instead of naming a product:
     * {@code documents/Loi_2016-48.pdf}, {@code Guide_Interne.docx}, {@code doc_12}.
     * Only real document extensions count — matching {@code .txt}, or a phrase such
     * as "le document 12", would eat prose, and prose is worth more than a leak.
     */
    private static final Pattern DOCUMENT_REFERENCE = Pattern.compile(
            "(?:[\\w.\\-/\\\\]+[/\\\\])?[\\w.\\-]+\\.(?:pdf|docx?|xlsx?|pptx?|odt)\\b"
                    + "|\\b(?:doc|src)[_#]\\d+\\b",
            Pattern.CASE_INSENSITIVE | Pattern.UNICODE_CASE);

    /**
     * A line that opens a provenance block: {@code Sources:}, {@code Références :},
     * {@code المصادر:} — and the labels an external retrieval service writes for the
     * same thing: {@code Resources:}, {@code Documents consulted}, {@code الموارد},
     * {@code الوثائق المستخدمة}. They belong here because appending what it read is how
     * those services answer, and a customer must never learn which documents were
     * consulted, whatever word the generator chose for the list.
     *
     * <p>A label alone on its line counts as a heading even without a colon — that is
     * how a list looks when it starts on the next line. Only these words are treated
     * that way, so ordinary prose, including a customer-facing "Documents requis :"
     * (which is the answer, not a citation), is never mistaken for one.
     */
    private static final Pattern SOURCE_HEADING = Pattern.compile(
            "(?im)^[ \\t>\\-*]*\\d*[.)]?[ \\t]*(?:sources?(?:\\s+(?:utilis[ée]s?|consult[ée]s?))?"
                    + "|références?|references?|bibliographie|citations?"
                    + "|resources?(?:\\s+(?:used|consulted))?"
                    + "|documents?\\s+(?:used|consult[ée]s?)"
                    + "|المصادر|المصدر|الوثائق\\s*(?:المستخدمة|المرجعية|المقترحة)"
                    + "|المستندات\\s*(?:المقدمة|المستخدمة)"
                    + "|الموارد(?:\\s*المستخدمة)?|مراجع)"
                    + "[ \\t]*(?:[:：][ \\t]*(.*)|$)",
            Pattern.UNICODE_CASE | Pattern.CASE_INSENSITIVE);

    /** A trailing line that is nothing but a link or a file name — a hand-written source list. */
    private static final Pattern LINK_ONLY_LINE = Pattern.compile(
            "^(?:[-\\u2022*]|\\d+[.)])?[ \\t]*(?:\\[[^\\]]*\\]\\((?:https?://|www\\.|/)[^)\\s]*\\)"
                    + "|(?:https?://|www\\.)\\S+|\\S+\\.(?:pdf|docx?|xlsx?|pptx?|txt|md)\\b)[ \\t]*$",
            Pattern.CASE_INSENSITIVE);

    /** A bullet line left empty by the removals above. */
    private static final Pattern EMPTY_BULLET = Pattern.compile("(?m)^[ \\t]*(?:[-\\u2022*]|\\d+[.)])[ \\t]*$");

    /** Above this many lines a "Sources:" block is not a citation list any more; leave it be. */
    private static final int MAX_TRAILING_SOURCE_LINES = 12;
    /** A line longer than this reads as prose, not as a source entry. */
    private static final int MAX_SOURCE_LINE_CHARS = 180;
    /** A trailing link list longer than this is part of the answer, not a source list. */
    private static final int MAX_TRAILING_LINK_LINES = 6;

    private AnswerSanitizer() {
    }

    /**
     * A stored answer, made safe to show again.
     *
     * <p>History is read from the database, so it carries answers written before this
     * policy existed — and an engine's refusal prose with its list of consulted
     * documents is exactly the kind of row that was stored then. Deleting the row is
     * not an option: the exchange is the audit trail, and the admin console must keep
     * reading it as it happened. So a customer surface sanitises on read as well as on
     * write, and "we fixed the leak" stays true for a conversation opened again next
     * month.
     *
     * <p>Idempotent by construction — {@link #sanitize(String)} on already-clean text
     * returns the same text — so replaying a row never erodes an answer.
     */
    public static String sanitizeStored(String storedAnswer) {
        return storedAnswer == null ? null : sanitize(storedAnswer);
    }

    /**
     * The customer-facing form of an answer: same wording, no provenance.
     * Null/blank input is returned untouched so callers can stay branch-free.
     */
    public static String sanitize(String answer) {
        if (answer == null || answer.isBlank()) {
            return answer;
        }
        String text = answer;
        text = MARKDOWN_CITATION_LINK.matcher(text).replaceAll("");
        text = BRACKET_CITATION.matcher(text).replaceAll("");
        text = PARENTHETIC_CITATION.matcher(text).replaceAll("");
        text = MARKDOWN_LINK.matcher(text).replaceAll("$1");
        text = INTERNAL_LINK.matcher(text).replaceAll("");
        text = stripTrailingSourceBlock(text);
        text = stripTrailingLinkList(text);
        text = DOCUMENT_REFERENCE.matcher(text).replaceAll("");
        return tidy(text);
    }

    /**
     * Drops a trailing "Sources:" / "المصادر:" block — the label line and the short
     * list under it. A block is only removed when it really reads as a citation
     * list: at most {@link #MAX_TRAILING_SOURCE_LINES} lines, each shorter than
     * {@link #MAX_SOURCE_LINE_CHARS}. Anything longer is treated as the answer's
     * own wording and left intact, because truncating a customer's answer is worse
     * than leaving one visible mention.
     */
    private static String stripTrailingSourceBlock(String text) {
        Matcher matcher = SOURCE_HEADING.matcher(text);
        int start = -1;
        String inline = null;
        while (matcher.find()) {
            start = matcher.start();
            inline = matcher.group(1);
        }
        if (start < 0) {
            return text;
        }
        // What follows the label on its own line counts as much as the lines under
        // it: "Sources: nous restons disponibles pour toute question…" is the answer
        // talking, not a citation list, and it must survive.
        if (inline != null && inline.strip().length() > MAX_SOURCE_LINE_CHARS) {
            return text;
        }
        String tail = text.substring(start);
        String[] lines = tail.split("\n");
        if (lines.length > MAX_TRAILING_SOURCE_LINES + 1) {
            return text;
        }
        for (int i = 1; i < lines.length; i++) {
            if (lines[i].strip().length() > MAX_SOURCE_LINE_CHARS) {
                return text;
            }
        }
        return text.substring(0, start).stripTrailing();
    }

    /**
     * Drops a list of bare links / file names stuck to the end of the answer, with
     * or without a heading. Only the contiguous run at the very end counts, so a
     * link inside a sentence is untouched.
     */
    private static String stripTrailingLinkList(String text) {
        String[] lines = text.split("\n");
        int first = lines.length;
        int seen = 0;
        for (int i = lines.length - 1; i >= 0; i--) {
            String line = lines[i].strip();
            if (line.isEmpty()) {
                if (first == lines.length) {
                    first = i; // a blank line inside the run: tentatively excluded
                    continue;
                }
                continue;
            }
            if (LINK_ONLY_LINE.matcher(line).matches() && seen < MAX_TRAILING_LINK_LINES) {
                first = i;
                seen++;
                continue;
            }
            break;
        }
        if (seen < 2) {
            // One lone link is part of a sentence, not a link list.
            return text;
        }
        StringBuilder kept = new StringBuilder();
        for (int i = 0; i < first; i++) {
            kept.append(lines[i]).append('\n');
        }
        return kept.toString().stripTrailing();
    }

    /** Whitespace repair after removals: no empty bullets, no double spaces, no dangling punctuation. */
    private static String tidy(String text) {
        String out = text
                .replaceAll("(?m)[ \\t]+$", "")
                .replaceAll("(?m)^(?:[ \\t]*\\n){2,}", "\n")
                .replaceAll("[ \\t]{2,}", " ")
                // Only the separators that never take a space in front of them.
                // French and Arabic typography put one before ": ; ! ?" — collapsing
                // those would rewrite correct prose, and this method exists to repair
                // holes left by removals, not to restyle the answer.
                .replaceAll("[ \\t]+([,.\u060C])", "$1")
                .replaceAll("(?m)^(?:[-\\u2022*][ \\t]*)+(?=\\n)", "");
        out = EMPTY_BULLET.matcher(out).replaceAll("");
        out = out.replaceAll("\n{3,}", "\n\n");
        // A line left with nothing but a separator (e.g. "—") after the citation went away.
        out = out.replaceAll("(?m)^[ \\t]*[\\-\\u2014\\u00b7][ \\t]*$", "");
        out = out.replaceAll("\n{3,}", "\n\n");
        return out.strip();
    }

    /**
     * Streaming scrubber for the SSE path: tokens are relayed as they arrive, so a
     * citation can cross a chunk boundary ({@code "[S"} + {@code "1]"}). Text that
     * could still turn out to be a marker is held back briefly and released once it
     * resolves; everything else is forwarded immediately.
     *
     * <p>Bounded on purpose: a stream that never resolves its bracket is flushed
     * after {@link #MAX_PENDING} characters rather than stalling the answer. And
     * the terminal {@code done} frame carries the fully sanitised text, which the
     * front ends adopt as the bubble content — so this filter removes the visible
     * flash, while {@link #sanitize(String)} is the actual guarantee.
     */
    public static final class Stream {

        /** How much text may be withheld while a marker resolves. */
        private static final int MAX_PENDING = 48;
        /** Beyond this the held tail is force-flushed: a slow answer beats a stalled one. */
        private static final int HARD_LIMIT = MAX_PENDING * 4;

        private final StringBuilder pending = new StringBuilder();

        /** @return the part of the answer safe to show right now (possibly empty). */
        public String accept(String delta) {
            if (delta == null || delta.isEmpty()) {
                return "";
            }
            pending.append(delta);
            if (pending.length() <= MAX_PENDING) {
                return "";
            }
            String buffer = pending.toString();
            int safe = safeCutAt(buffer);
            if (safe <= 0) {
                if (buffer.length() < HARD_LIMIT) {
                    return "";
                }
                safe = buffer.length();
            }
            String emit = buffer.substring(0, safe);
            pending.setLength(0);
            pending.append(buffer.substring(safe));
            return sanitize(emit);
        }

        /** Releases what the stream ended with; the final answer replaces the bubble anyway. */
        public String flush() {
            String rest = pending.toString();
            pending.setLength(0);
            return sanitize(rest);
        }

        /**
         * Index up to which the buffer cannot still be rewritten by a marker: the
         * start of an unclosed bracket, of a URL, or of a line that looks like the
         * beginning of a provenance heading.
         */
        private static int safeCutAt(String buffer) {
            int cut = buffer.length();
            int open = lastOpenBracket(buffer);
            if (open >= 0) {
                cut = Math.min(cut, open);
            }
            String tail = buffer.substring(Math.max(0, buffer.length() - MAX_PENDING - 8));
            int link = Math.max(tail.lastIndexOf("http"), Math.max(tail.lastIndexOf("www."), tail.lastIndexOf("://")));
            if (link >= 0) {
                cut = Math.min(cut, buffer.length() - (tail.length() - link));
            }
            int lineStart = lastLineStartCandidate(buffer);
            if (lineStart >= 0) {
                cut = Math.min(cut, lineStart);
            }
            return cut;
        }

        /** A {@code [} or {@code (} with no closing partner yet: a marker may be forming. */
        private static int lastOpenBracket(String buffer) {
            for (int i = buffer.length() - 1; i >= 0 && i >= buffer.length() - MAX_PENDING - 1; i--) {
                char c = buffer.charAt(i);
                if (c == ']' || c == ')') {
                    return -1;
                }
                if (c == '[' || c == '(') {
                    return i;
                }
            }
            return -1;
        }

        /**
         * Holds back the tail while the current line could still become a provenance
         * heading ("Sources", "المصادر", "Références" being typed right now).
         */
        private static int lastLineStartCandidate(String buffer) {
            int nl = buffer.lastIndexOf('\n');
            int lineStart = nl < 0 ? 0 : nl + 1;
            String line = buffer.substring(lineStart);
            String probe = line.stripLeading();
            if (probe.length() >= 12 || probe.isEmpty()) {
                return -1;
            }
            String lower = probe.toLowerCase(java.util.Locale.ROOT);
            // The same vocabulary SOURCE_HEADING knows, so a heading is never released
            // to the browser while its list is still arriving on the line below.
            boolean couldBeHeading = lower.startsWith("source") || lower.startsWith("resource")
                    || lower.startsWith("ref") || lower.startsWith("réf") || lower.startsWith("citation")
                    || lower.startsWith("biblio")
                    || probe.startsWith("المصادر") || probe.startsWith("المصدر")
                    || probe.startsWith("الموارد") || probe.startsWith("الوثائق")
                    || probe.startsWith("مراجع")
                    || lower.endsWith(":");
            return couldBeHeading ? lineStart : -1;
        }
    }
}
