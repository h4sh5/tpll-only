/*
 * tpll-only &mdash; standalone /tpll and /tpllcalc for modern Fabric.
 */
package dev.tpllonly.command;

import net.minecraft.ChatFormatting;
import net.minecraft.network.chat.ClickEvent;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.HoverEvent;
import net.minecraft.network.chat.MutableComponent;
import net.minecraft.network.chat.Style;

import java.net.URI;

/**
 * Small helpers for chat output that can be clicked to copy a value, run a command, or prefill the
 * chat box.
 */
public final class Chat {

	private Chat() {
	}

	/**
	 * @return {@code display}, styled so that clicking it copies {@code value} to the clipboard
	 */
	public static MutableComponent copy(String display, String value) {
		return copy(Component.literal(display), value);
	}

	/**
	 * @return {@code display}, styled so that clicking it copies {@code value} to the clipboard
	 */
	public static MutableComponent copy(Component display, String value) {
		return styled(display, Style.EMPTY
				.withClickEvent(new ClickEvent.CopyToClipboard(value))
				.withHoverEvent(new HoverEvent.ShowText(
						Component.literal("Click to copy: ").withStyle(ChatFormatting.YELLOW)
								.append(Component.literal(value).withStyle(ChatFormatting.WHITE)))));
	}

	/**
	 * @return {@code display}, styled so that clicking it runs {@code command}
	 */
	public static MutableComponent run(Component display, String command) {
		return styled(display, Style.EMPTY
				.withClickEvent(new ClickEvent.RunCommand(command))
				.withHoverEvent(new HoverEvent.ShowText(
						Component.literal("Click to run: ").withStyle(ChatFormatting.YELLOW)
								.append(Component.literal(command).withStyle(ChatFormatting.WHITE)))));
	}

	/**
	 * @return {@code display}, styled so that shift-clicking it puts {@code command} in the chat box
	 */
	public static MutableComponent suggest(Component display, String command) {
		return styled(display, Style.EMPTY
				.withClickEvent(new ClickEvent.SuggestCommand(command))
				.withHoverEvent(new HoverEvent.ShowText(
						Component.literal("Shift-click to insert: ").withStyle(ChatFormatting.YELLOW)
								.append(Component.literal(command).withStyle(ChatFormatting.WHITE)))));
	}

	/**
	 * @return {@code display}, styled so that clicking it opens {@code url} in the player's browser
	 */
	public static MutableComponent openUrl(Component display, String url) {
		return styled(display, Style.EMPTY
				.withClickEvent(new ClickEvent.OpenUrl(URI.create(url)))
				.withHoverEvent(new HoverEvent.ShowText(
						Component.literal("Click to open: ").withStyle(ChatFormatting.YELLOW)
								.append(Component.literal(url).withStyle(ChatFormatting.WHITE)))));
	}

	/** underlines text that is interactive, so it reads as a button */
	public static MutableComponent actionable(String text) {
		return Component.literal(text).withStyle(ChatFormatting.AQUA, ChatFormatting.UNDERLINE);
	}

	/** the "[ ... ]" affordance put in front of clickable values */
	public static MutableComponent bracketed(MutableComponent content) {
		return Component.literal("[ ").withStyle(ChatFormatting.DARK_GRAY).append(content)
				.append(Component.literal(" ]").withStyle(ChatFormatting.DARK_GRAY));
	}

	private static MutableComponent styled(Component display, Style style) {
		MutableComponent component = display.copy();
		component.setStyle(style);
		return component;
	}
}
