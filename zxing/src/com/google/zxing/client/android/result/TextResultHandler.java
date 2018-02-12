/*
 * Copyright (C) 2008 ZXing authors
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

package com.google.zxing.client.android.result;

import java.util.Arrays;
import java.util.Collections;
import java.util.List;

import android.app.Activity;
import android.text.format.DateFormat;

import com.google.zxing.Result;
import com.google.zxing.client.result.ParsedResult;
import com.zhiyong.code.R;

/**
 * This class handles TextParsedResult as well as unknown formats. It's the fallback handler.
 *
 * @author dswitkin@google.com (Daniel Switkin)
 */
public final class TextResultHandler extends ResultHandler {

  private static final int[] buttons = {
      R.string.button_share_by_sms,
      R.string.button_custom_product_search,
  };
  
  public TextResultHandler(Activity activity, ParsedResult result, Result rawResult) {
    super(activity, result, rawResult);
  }

  @Override
  public int getButtonCount() {
    return hasCustomProductSearch() ? buttons.length : buttons.length - 1;
  }

  @Override
  public int getButtonText(int index) {
    return buttons[index];
  }

  @Override
  public void handleButtonPress(String User,int index) {
    String text = getResult().getDisplayResult();
    long sysTime = System.currentTimeMillis();
    CharSequence sysTimeStr = DateFormat.format("yyyy-MM-dd hh:mm:ss", sysTime);
    String Msg = User+"#"+text+"#"+sysTimeStr +"#校验码"+generateWord();
    switch (index) {
      case 0:
        shareBySMS(Msg);
        break;
    }
  }
  private String generateWord() {  
      String[] beforeShuffle = new String[] { "2", "3", "4", "5", "6", "7",  
              "8", "9", "A", "B", "C", "D", "E", "F", "G", "H", "I", "J",  
              "K", "L", "M", "N", "O", "P", "Q", "R", "S", "T", "U", "V",  
              "W", "X", "Y", "Z" };  
      List list = Arrays.asList(beforeShuffle);  
      Collections.shuffle(list);  
      StringBuilder sb = new StringBuilder();  
      for (int i = 0; i < list.size(); i++) {  
          sb.append(list.get(i));  
      }  
      String afterShuffle = sb.toString();  
      String result = afterShuffle.substring(5, 9);  
      return result;  
  }
  @Override
  public int getDisplayTitle() {
    return R.string.result_text;
  }
}
