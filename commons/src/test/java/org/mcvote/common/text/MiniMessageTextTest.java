package org.mcvote.common.text;

import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.event.ClickEvent;
import net.kyori.adventure.text.serializer.plain.PlainTextComponentSerializer;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

class MiniMessageTextTest {

    private static final PlainTextComponentSerializer PLAIN = PlainTextComponentSerializer.plainText();

    @Test
    void buildsAClickEventFromAnOpenUrlTag() {
        Component rendered = MiniMessageText.render("<click:open_url:'https://mcvote.org/vote'>Vote</click>");

        ClickEvent click = clickOf(rendered);
        assertNotNull(click);
        assertEquals(ClickEvent.Action.OPEN_URL, click.action());
        assertEquals("https://mcvote.org/vote", click.value());
    }

    @Test
    void keepsAQueryStringIntactInsideAClickTag() {
        String url = "https://site.example/vote?server=7&ref=abc";
        String line = "<click:open_url:'" + MiniMessageText.escape(url) + "'>Vote</click>";

        ClickEvent click = clickOf(MiniMessageText.render(line));
        assertNotNull(click);
        assertEquals(url, click.value(), "&ref must survive: it is not a colour code here");
    }

    @Test
    void stillReadsTheOldAmpersandCodes() {
        assertEquals("Thanks for voting", PLAIN.serialize(MiniMessageText.render("&aThanks for voting")));
        assertEquals("§aThanks for voting", MiniMessageText.toLegacySection("&aThanks for voting"));
        assertEquals("§8» Vote", MiniMessageText.toLegacySection("&8» Vote"));
    }

    @Test
    void aLineWithMiniMessageTagsIsNeverReadAsLegacyCodes() {
        Component rendered = MiniMessageText.render("<gray>ref=1&b=2");

        assertEquals("ref=1&b=2", PLAIN.serialize(rendered), "&b must stay text, not become aqua");
    }

    @Test
    void escapedValuesCannotInjectMarkup() {
        String hostile = "<red>evil</red>";
        Component rendered = MiniMessageText.render("<gray>" + MiniMessageText.escape(hostile));

        assertEquals(hostile, PLAIN.serialize(rendered));
    }

    @Test
    void toLegacyKeepsTheTextAndDropsOnlyTheInteraction() {
        String legacy = MiniMessageText.toLegacySection(
                "<green><click:open_url:'https://x.test'>Vote here</click>");

        assertEquals("§aVote here", legacy);
    }

    @Test
    void emptyInputRendersToNothing() {
        assertEquals("", PLAIN.serialize(MiniMessageText.render("")));
        assertEquals("", PLAIN.serialize(MiniMessageText.render(null)));
        assertEquals("", MiniMessageText.escape(null));
    }

    @Test
    void plainTextCarriesNoClickEvent() {
        assertNull(clickOf(MiniMessageText.render("just words")));
        assertTrue(PLAIN.serialize(MiniMessageText.render("just words")).contains("just words"));
    }

    private static ClickEvent clickOf(Component component) {
        if (component.clickEvent() != null) {
            return component.clickEvent();
        }
        for (Component child : component.children()) {
            ClickEvent found = clickOf(child);
            if (found != null) {
                return found;
            }
        }
        return null;
    }
}
