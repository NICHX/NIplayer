package com.nichx.niplayer.subtitle.format;

import com.nichx.niplayer.subtitle.info.Caption;
import com.nichx.niplayer.subtitle.info.Style;
import com.nichx.niplayer.subtitle.info.Time;
import com.nichx.niplayer.subtitle.info.TimedTextObject;

import org.mozilla.universalchardet.ReaderFactory;

import java.io.BufferedReader;
import java.io.File;
import java.io.IOException;
import java.nio.charset.Charset;
import java.util.ArrayList;
import java.util.Iterator;

/**
 * This class represents the .SRT subtitle format
 * <br><br>
 * Copyright (c) 2012 J. David Requejo <br>
 * j[dot]david[dot]requejo[at] Gmail
 * <br><br>
 * Permission is hereby granted, free of charge, to any person obtaining a copy of this software
 * and associated documentation files (the "Software"), to deal in the Software without restriction,
 * including without limitation the rights to use, copy, modify, merge, publish, distribute,
 * sublicense, and/or sell copies of the Software, and to permit persons to whom the Software
 * is furnished to do so, subject to the following conditions:
 * <br><br>
 * The above copyright notice and this permission notice shall be included in all copies
 * or substantial portions of the Software.
 * <br><br>
 * THE SOFTWARE IS PROVIDED "AS IS", WITHOUT WARRANTY OF ANY KIND, EXPRESS OR IMPLIED,
 * INCLUDING BUT NOT LIMITED TO THE WARRANTIES OF MERCHANTABILITY, FITNESS FOR A PARTICULAR
 * PURPOSE AND NONINFRINGEMENT. IN NO EVENT SHALL THE AUTHORS OR COPYRIGHT HOLDERS BE LIABLE
 * FOR ANY CLAIM, DAMAGES OR OTHER LIABILITY, WHETHER IN AN ACTION OF CONTRACT, TORT OR
 * OTHERWISE, ARISING FROM, OUT OF OR IN CONNECTION WITH THE SOFTWARE OR THE USE OR OTHER
 * DEALINGS IN THE SOFTWARE.
 *
 * @author J. David Requejo
 *
 */
public class FormatSRT implements TimedTextFileFormat {

	public TimedTextObject parseFile(File file) throws IOException {
		return parseFile(file, null);
	}

	public TimedTextObject parseFile(File file, Charset isCharset) throws IOException {

		TimedTextObject tto = new TimedTextObject();
		Caption caption = new Caption();
		int captionNumber = 1;
		boolean allGood;

		//first lets load the file
		//creating a reader with correct encoding
		Charset defaultCharset = Charset.forName("GBK");
		BufferedReader br = ReaderFactory.createBufferedReader(file, defaultCharset);

		//the file name is saved
		tto.fileName = file.getName();

		String line = br.readLine();
		if (line != null){
			line = line.replace("\uFEFF", ""); //remove BOM character
		}
		int lineCounter = 0;
		try {
			while(line!=null){
				line = line.trim();
				lineCounter++;
				//if its a blank line, ignore it, otherwise...
				if (!line.isEmpty()){
					allGood = false;
					//the first thing should be an increasing number
					try {
						int num = Integer.parseInt(line);
						if (num != captionNumber)
							throw new Exception();
						else {
							captionNumber++;
							allGood = true;
						}
					} catch (Exception e) {
						tto.warnings+= captionNumber + " expected at line " + lineCounter;
						tto.warnings+= "\n skipping to next line\n\n";
					}
					if (allGood){
						//we go to next line, here the begin and end time should be found
						try {
							lineCounter++;
							line = br.readLine().trim();
							String start = line.substring(0, 12);
							String end = line.substring(line.length()-12);
							Time time = new Time("hh:mm:ss,ms",start);
							caption.start = time;
							time = new Time("hh:mm:ss,ms",end);
							caption.end = time;
						} catch (Exception e){
							tto.warnings += "incorrect time format at line "+lineCounter;
							allGood = false;
						}
					}
					if (allGood){
						//we go to next line where the caption text starts
						lineCounter++;
						line = br.readLine().trim();
						String text = "";
						while (!line.isEmpty()){
							text+=line+"<br />";
							line = br.readLine().trim();
							lineCounter++;
						}
						caption.content = text;
						// rawContent 才是渲染链路读的字段（AssOverrideParser.parse → SubtitleEngine）。
						// 只写 content 时 rawContent 保持空串 → 本格式解析出的每条字幕 span 为空 →
						// SubtitleEngine.update 把它们全部丢弃 → captions 非空、无异常、无提示，
						// 屏幕上什么都没有。循环每行都补了尾随标记，去掉最后那个，避免多出一个空换行。
						String rawText = text.endsWith("<br />")
								? text.substring(0, text.length() - "<br />".length())
								: text;
						caption.rawContent = toAssMarkup(rawText);
						long key = caption.start.mseconds;
						//in case the key is already there, we increase it by a millisecond, since no duplicates are allowed
						while (tto.captions.containsKey(key)) key++;
						if (key != caption.start.mseconds)
							tto.warnings+= "caption with same start time found...\n\n";
						//we add the caption.
						tto.captions.put(key, caption);
					}
					//we go to next blank
					while (!line.isEmpty()) {
						line = br.readLine().trim();
						lineCounter++;
					}
					caption = new Caption();
				}
				line = br.readLine();
			}

		}  catch (NullPointerException e){
			tto.warnings+= "unexpected end of file, maybe last caption is not complete.\n\n";
		} finally{
	        //we close the reader
			br.close();
	     }

		tto.built = true;
		return tto;
	}


	public String[] toFile(TimedTextObject tto) {

		//first we check if the TimedTextObject had been built, otherwise...
		if(!tto.built)
			return null;

		//we will write the lines in an ArrayList,
		int index = 0;
		//the minimum size of the file is 4*number of captions, so we'll take some extra space.
		ArrayList<String> file = new ArrayList<>(5 * tto.captions.size());
		//we iterate over our captions collection, they are ordered since they come from a TreeMap
		java.util.Collection<Caption> c = tto.captions.values();
		Iterator<Caption> itr = c.iterator();
		int captionNumber = 1;

		while(itr.hasNext()){
			//new caption
			Caption current = itr.next();
			//number is written
			file.add(index++, Integer.toString(captionNumber++));
			//we check for offset value:
			if(tto.offset != 0){
				current.start.mseconds += tto.offset;
				current.end.mseconds += tto.offset;
			}
			//time is written
			file.add(index++,current.start.getTime("hh:mm:ss,ms")+" --> "+current.end.getTime("hh:mm:ss,ms"));
			//offset is undone
			if(tto.offset != 0){
				current.start.mseconds -= tto.offset;
				current.end.mseconds -= tto.offset;
			}
			//text is added
			String[] lines = cleanTextForSRT(current);
			int i=0;
			while(i<lines.length)
				file.add(index++,""+lines[i++]);
			//we add the next blank line
			file.add(index++,"");
		}

		String[] toReturn = new String[file.size()];
		for (int i = 0; i < toReturn.length; i++) {
			toReturn[i] = file.get(i);
		}
		return toReturn;
	}


	/* PRIVATE METHODS */

	/** SRT 文本里的行分隔标记（[Caption.content] 用）。 */
	private static final String SRT_LINE_BREAK = "<br />";

	/**
	 * 形如标签的 `&lt;...&gt;`：必须以字母或 `/` 开头。
	 *
	 * 收紧首字符是为了不误伤正文里的比较符号 —— `5 &lt; 10 &gt; 3` 若按 `<[^>]*>` 匹配会把
	 * `< 10 >` 整段当成标签删掉。
	 */
	private static final java.util.regex.Pattern SRT_TAG =
			java.util.regex.Pattern.compile("<[/a-zA-Z][^>]*>");

	/** `<font color="RRGGBB">` 里的颜色值（容忍引号与 `#`）。 */
	private static final java.util.regex.Pattern FONT_COLOR =
			java.util.regex.Pattern.compile("(?i)color\\s*=\\s*[\"']?#?([0-9a-f]{6})[\"']?");

	/**
	 * SRT 文本 → 渲染链路（{@link com.nichx.niplayer.subtitle.renderer.AssOverrideParser}）认识的标记。
	 *
	 * 渲染只认 ASS override tag：SDH / 字幕组常用的 HTML 标记若原样带进去，会**整段显示成字面文本**
	 *（`&lt;i&gt;Hello&lt;/i&gt;` 直接画在屏幕上），同时斜体、加粗、颜色等意图全部丢失。
	 * 这里做最小映射：
	 * <ul>
	 *   <li>行分隔 `&lt;br /&gt;` → `\N`（ASS 换行）</li>
	 *   <li>`&lt;i&gt;`、`&lt;b&gt;`、`&lt;u&gt;`、`&lt;s&gt;` 及其闭合标签 → 对应的
	 *       ASS override tag（斜体 / 加粗 / 下划线 / 删除线，开与关）</li>
	 *   <li>`&lt;font color="#RRGGBB"&gt;` → ASS 颜色 tag；`&lt;/font&gt;` → 无操作的颜色 tag</li>
	 *   <li>其余标签（`&lt;v&gt;`、`&lt;c.xxx&gt;` 等）丢弃，避免原样显示</li>
	 * </ul>
	 * 实体解码放在**标签处理之后**：否则 `&amp;lt;i&amp;gt;` 会先变成真标签而被误当样式。
	 */
	private static String toAssMarkup(String srtText) {
		String withBreaks = srtText.replace(SRT_LINE_BREAK, "\\N");
		java.util.regex.Matcher matcher = SRT_TAG.matcher(withBreaks);
		StringBuffer out = new StringBuffer();
		while (matcher.find()) {
			matcher.appendReplacement(
					out,
					java.util.regex.Matcher.quoteReplacement(toAssTag(matcher.group()))
			);
		}
		matcher.appendTail(out);
		return decodeEntities(out.toString());
	}

	/** 单个 SRT 标签 → ASS override tag；无法映射的返回空串（丢弃）。 */
	private static String toAssTag(String tag) {
		String lower = tag.trim().toLowerCase();
		if (lower.equals("<i>")) return "{\\i1}";
		if (lower.equals("</i>")) return "{\\i0}";
		if (lower.equals("<b>")) return "{\\b1}";
		if (lower.equals("</b>")) return "{\\b0}";
		if (lower.equals("<u>")) return "{\\u1}";
		if (lower.equals("</u>")) return "{\\u0}";
		if (lower.equals("<s>")) return "{\\s1}";
		if (lower.equals("</s>")) return "{\\s0}";
		if (lower.startsWith("</font")) return "{\\c}";
		if (lower.startsWith("<font")) {
			java.util.regex.Matcher colorMatcher = FONT_COLOR.matcher(tag);
			if (!colorMatcher.find()) return "";
			String rgb = colorMatcher.group(1);
			// ASS 颜色是 AABBGGRR：AA=00 表示不透明，随后依次是 B、G、R
			return "{\\c&H00"
					+ rgb.substring(4, 6) + rgb.substring(2, 4) + rgb.substring(0, 2) + "&}";
		}
		return "";
	}

	/** 常见 HTML 实体解码（先长后短，避免二次解码）。 */
	private static String decodeEntities(String text) {
		return text
				.replace("&nbsp;", " ")
				.replace("&lt;", "<")
				.replace("&gt;", ">")
				.replace("&quot;", "\"")
				.replace("&#39;", "'")
				.replace("&amp;", "&");
	}

	/**
	 * This method cleans caption.content of XML and parses line breaks.
	 *
	 */
	private String[] cleanTextForSRT(Caption current) {
		String[] lines;
		String text = current.content;
		//add line breaks
		lines = text.split("<br />");
		//clean XML
		for (int i = 0; i < lines.length; i++){
			//this will destroy all remaining XML tags
			lines[i] = lines[i].replaceAll("<.*?>", "");
		}
		return lines;
	}

}
