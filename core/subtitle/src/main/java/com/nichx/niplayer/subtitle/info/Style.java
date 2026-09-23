package com.nichx.niplayer.subtitle.info;

public class Style {

	private static int styleCounter;

	/**
	 * Constructor that receives a String to use a its identifier
	 *
	 * @param styleName = identifier of this style
	 */
	public Style(String styleName) {
		this.iD = styleName;
	}

	/**
	 * Constructor that receives a String with the new styleName and a style to copy
	 *
	 * @param styleName
	 * @param style
	 */
	public Style(String styleName, Style style) {
		this.iD = styleName;
		this.font = style.font;
		this.fontSize = style.fontSize;
		this.color = style.color;
		this.outlineColor = style.outlineColor;
		this.backgroundColor = style.backgroundColor;
		this.textAlign = style.textAlign;
		this.italic = style.italic;
		this.underline = style.underline;
		this.bold = style.bold;

	}

	/* ATTRIBUTES */
	public String iD;
	public String font;
	public String fontSize;
	/**colors are stored as 8 chars long RGBA*/
	public String color;
	/**
	 * ASS `OutlineColour`（描边色），RRGGBBAA。
	 *
	 * 渲染侧原先误用 {@link #backgroundColor}（= ASS `BackColour`，即阴影色）当描边色 ——
	 * 两者在 ASS 里是不同字段（BackColour 常常是半透明黑，拿它当描边会让描边透明度出错）。
	 * SSA（v4.00 非 +）样式没有该字段，保持 null，渲染侧回退到 {@link #backgroundColor}。
	 */
	public String outlineColor;
	public String backgroundColor;
	public String textAlign = "";

	public boolean italic;
	public boolean bold;
	public boolean underline;

	/* METHODS */

	/**
	 * To get the string containing the hex value to put into color or background color
	 *
	 * @param format supported: "name", "&HBBGGRR", "&HAABBGGRR", "decimalCodedBBGGRR", "decimalCodedAABBGGRR"
	 * @param value RRGGBBAA string
	 * @return
	 */
	public static String getRGBValue(String format, String value){
		String color = null;
		if (format.equalsIgnoreCase("name")){
			//standard color format from W3C
			if (value.equals("transparent"))
				color = "00000000";
			else if (value.equals("black"))
				color = "000000ff";
			else if (value.equals("silver"))
				color = "c0c0c0ff";
			else if (value.equals("gray"))
				color = "808080ff";
			else if (value.equals("white"))
				color = "ffffffff";
			else if (value.equals("maroon"))
				color = "800000ff";
			else if (value.equals("red"))
				color = "ff0000ff";
			else if (value.equals("purple"))
				color = "800080ff";
			else if (value.equals("fuchsia"))
				color = "ff00ffff";
			else if (value.equals("magenta"))
				color = "ff00ffff";
			else if (value.equals("green"))
				color = "008000ff";
			else if (value.equals("lime"))
				color = "00ff00ff";
			else if (value.equals("olive"))
				color = "808000ff";
			else if (value.equals("yellow"))
				color = "ffff00ff";
			else if (value.equals("navy"))
				color = "000080ff";
			else if (value.equals("blue"))
				color = "0000ffff";
			else if (value.equals("teal"))
				color = "008080ff";
			else if (value.equals("aqua"))
				color = "00ffffff";
			else if (value.equals("cyan"))
				color = "00ffffff";
		} else if (format.equalsIgnoreCase("&HBBGGRR")){
			//hex format from SSA：BBGGRR → RRGGBBAA
			color = bgrToRgb(stripHexMarkers(value));
		} else if (format.equalsIgnoreCase("&HAABBGGRR")){
			//hex format from ASS：AABBGGRR → RRGGBBAA
			color = abgrToRgb(stripHexMarkers(value));
		} else if (format.equalsIgnoreCase("decimalCodedBBGGRR")){
			//normal format from SSA：十进制 BBGGRR → RRGGBBAA
			String hex = decimalToHex(value, 6);
			color = hex == null ? null : bgrToRgb(hex);
		}  else if (format.equalsIgnoreCase("decimalCodedAABBGGRR")){
			//normal format from ASS：十进制 AABBGGRR → RRGGBBAA
			String hex = decimalToHex(value, 8);
			color = hex == null ? null : abgrToRgb(hex);
		}
		 return color;
	}

	/** 去掉 `&H`/`&h` 前缀与结尾的 `&`（ASS 颜色字面量的常见写法）。 */
	private static String stripHexMarkers(String value) {
		String hex = value.trim();
		if (hex.startsWith("&H") || hex.startsWith("&h")) hex = hex.substring(2);
		if (hex.endsWith("&")) hex = hex.substring(0, hex.length() - 1);
		return hex.trim();
	}

	/** 十进制颜色值 → 定长十六进制（不足左侧补 0）。无法解析时返回 null。 */
	private static String decimalToHex(String value, int length) {
		try {
			return padHex(Long.toHexString(Long.parseLong(value.trim())), length);
		} catch (NumberFormatException e) {
			return null;
		}
	}

	/** 右对齐到 [length] 位：不足左侧补 0，超出取低 [length] 位。 */
	private static String padHex(String hex, int length) {
		if (hex.isEmpty()) hex = "0";
		if (hex.length() >= length) return hex.substring(hex.length() - length);
		StringBuilder sb = new StringBuilder();
		for (int i = hex.length(); i < length; i++) sb.append('0');
		sb.append(hex);
		return sb.toString();
	}

	/** BBGGRR → RRGGBBAA（alpha 视为不透明 ff）。 */
	private static String bgrToRgb(String hex) {
		String padded = padHex(hex, 6);
		String bb = padded.substring(0, 2);
		String gg = padded.substring(2, 4);
		String rr = padded.substring(4, 6);
		return (rr + gg + bb + "ff").toLowerCase(java.util.Locale.ROOT);
	}

	/**
	 * AABBGGRR → RRGGBBAA（消费方按 RRGGBBAA 解析）。
	 *
	 * ⚠️ 必须自己成对取字节并**反转 alpha**：
	 * - 原实现用 `append(value, 6, 7)` 这类**单字符**切片，产出 5 位色串，而消费方
	 *   （`SubtitleEngine.parseStyleColor` / `AssOverrideParser.parseStyleColor`）只接受 6 或 8 位
	 *   —— 结果 ASS Style 自带的主色/边框色被静默丢弃，一律回退到用户在设置里选的颜色；
	 * - ASS 的 alpha 语义与 RGBA 相反（`00` 不透明 / `FF` 透明），而消费方直读最后一字节当作
	 *   RGBA alpha。若原样搬运，最常见的 `&H00FFFFFF` 会变成**全透明**（字幕直接看不见）。
	 */
	private static String abgrToRgb(String hex) {
		String padded = padHex(hex, 8);
		String aa = padded.substring(0, 2);
		String bb = padded.substring(2, 4);
		String gg = padded.substring(4, 6);
		String rr = padded.substring(6, 8);
		int alpha = 255 - Integer.parseInt(aa, 16);
		return (rr + gg + bb + String.format("%02x", alpha)).toLowerCase(java.util.Locale.ROOT);
	}

	public static String defaultID() {
		return "default"+styleCounter++;
	}

    @Override
    public String toString() {
        return "Style{" +
                "id='" + iD + '\'' +
                ", font='" + font + '\'' +
                ", fontSize='" + fontSize + '\'' +
                ", color='" + color + '\'' +
                ", outlineColor='" + outlineColor + '\'' +
                ", backgroundColor='" + backgroundColor + '\'' +
                ", textAlign='" + textAlign + '\'' +
                ", italic=" + italic +
                ", bold=" + bold +
                ", underline=" + underline +
                '}';
    }
}
