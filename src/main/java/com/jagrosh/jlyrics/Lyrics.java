/*
 * Copyright 2018 John Grosh (john.a.grosh@gmail.com).
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *      http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package com.jagrosh.jlyrics;

public class Lyrics
{
    private final String title;
    private final String author;
    private final String content;
    private final String url;
    private final String source;

    protected Lyrics(String title, String author, String content, String url, String source)
    {
        this.title = title;
        this.author = author;
        this.content = content;
        this.url = url;
        this.source = source;
    }

    public String getTitle()
    {
        return title;
    }

    public String getAuthor()
    {
        return author;
    }

    public String getContent()
    {
        return content;
    }

    public String getURL()
    {
        return url;
    }

    public String getSource()
    {
        return source;
    }
}
