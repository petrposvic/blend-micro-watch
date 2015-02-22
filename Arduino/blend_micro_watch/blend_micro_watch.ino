#include <SPI.h>
#include <Wire.h>
#include <boards.h>
#include <RBL_nRF8001.h>
#include <Adafruit_GFX.h>
#include <Adafruit_SSD1306.h>
#include "Clock.h"

// Turn off the display if there is no notification
// #define SAVE_BATTERY

#define DISPLAY    5
#define VIBRATOR   8
#define OLED_DC    9
#define OLED_CS    10
#define OLED_CLK   11
#define OLED_MOSI  12
#define OLED_RESET 13

Adafruit_SSD1306 display(OLED_MOSI, OLED_CLK, OLED_DC, OLED_RESET, OLED_CS);
Clock clock;

void setup() {  
  ble_begin();

  Serial.begin(115200);

  // Display
  display.begin(SSD1306_SWITCHCAPVCC);
  display.display();
  display.setTextSize(1);
  display.setTextColor(WHITE);

  display.clearDisplay();
  display.setCursor(0, 0);
  display.println("--------------------- ");
  display.println("| Blend Micro Watch | ");
  display.println("--------------------- ");
  display.println();
  display.println("       Welcome        ");
  display.display();

  pinMode(DISPLAY, OUTPUT);
  digitalWrite(DISPLAY, HIGH);

  pinMode(VIBRATOR, OUTPUT);
  digitalWrite(VIBRATOR, HIGH); // Pullup

  Serial.println("setup ok");
}

// Show setup message for some time
long timer = 5000;
boolean new_msg = true;

// Check out ble_connected(). If state changes then vibrate.
boolean ble_state = false;

void loop() {

  if (timer < millis()) {
    if (new_msg) {
      new_msg = false;

#ifdef SAVE_BATTERY
    Serial.println("turn off display");
    digitalWrite(DISPLAY, LOW);
#endif

    }

    clock.proceed();
    show_clock();
  }

  if (ble_available()) {
    while (ble_available()) {
      char ch = ble_read();

      // Show notification
      if (ch == '~') {
        Serial.println();

#ifdef SAVE_BATTERY
        Serial.print("turn on display");
        digitalWrite(DISPLAY, HIGH);
#endif

        vibrate();
        new_msg = true;
      } else

      // Set clock
      if (ch == '&') {
        int hms[] = {0, 0, 0};
        for (int i = 0; i < 6; i++) {
          if (!ble_available()) {
            Serial.println("error in setting clock");
            break;
          }

          ch = ble_read();
          Serial.print(ch);

          if (i % 2) {
            hms[i / 2] += ch - 48;
          } else {
            hms[i / 2] += (ch - 48) * 10;
          }
        }

        clock.h = hms[0];
        clock.m = hms[1];
        clock.s = hms[2];
        Serial.println();
        Serial.print("setting clock ");
        Serial.print(clock.h);
        Serial.print(":");
        Serial.print(clock.m);
        Serial.print(":");
        Serial.print(clock.s);
        Serial.println();
      } else

      {
        Serial.write(ch);

        // Each char prolong display timer
        timer = millis() + 1000 * 5;

        display.print(ch);
        display.display();
      }
    }

    Serial.println();
  }

  if (Serial.available()) {
    delay(5);

    while (Serial.available()) {
      ble_write(Serial.read());
    }
  }

  boolean tmp_state = ble_connected();
  if (tmp_state != ble_state) {
    ble_state = tmp_state;
    vibrate();
  }

  ble_do_events();
}

void show_clock() {
  display.clearDisplay();
  display.setCursor(0, 0);
  display.print("       ");
  if (clock.h < 10) display.print("0");
  display.print(clock.h);
  display.print(":");
  if (clock.m < 10) display.print("0");
  display.print(clock.m);
  display.print(":");
  if (clock.s < 10) display.print("0");
  display.print(clock.s);
  display.println();
  display.display();
}

void vibrate() {
  digitalWrite(VIBRATOR, LOW);
  delay(1000);
  digitalWrite(VIBRATOR, HIGH);
}

