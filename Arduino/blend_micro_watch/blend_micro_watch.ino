#include <SPI.h>
#include <Wire.h>
#include <boards.h>
#include <RBL_nRF8001.h>
#include <Adafruit_GFX.h>
#include <Adafruit_SSD1306.h>

#define DISPLAY    5
#define VIBRATOR   8
#define OLED_DC    9
#define OLED_CS    10
#define OLED_CLK   11
#define OLED_MOSI  12
#define OLED_RESET 13

Adafruit_SSD1306 display(OLED_MOSI, OLED_CLK, OLED_DC, OLED_RESET, OLED_CS);

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
  display.print("Hello!");
  display.display();

  pinMode(DISPLAY, OUTPUT);
  digitalWrite(DISPLAY, HIGH);

  pinMode(VIBRATOR, OUTPUT);
  digitalWrite(VIBRATOR, HIGH); // Pullup

  Serial.println("setup ok");
}

unsigned char len = 0;

// Show setup message for some time
long timer = 5000;
boolean new_msg = true;

// Check out ble_connected(). If state changes then vibrate.
boolean ble_state = false;

void loop() {

  // Turn off the display after some time
  if (new_msg && timer < millis()) {
    Serial.println("turn off display");
    new_msg = false;
    digitalWrite(DISPLAY, LOW);
  }

  if (ble_available()) {
    while (ble_available()) {
      char ch = ble_read();

      if (ch == '~') {
        Serial.println();
        Serial.print("turn on display");
        digitalWrite(DISPLAY, HIGH);

        vibrate();
        timer = millis() + 1000 * 5;
        new_msg = true;
      } else {
        Serial.write(ch);

        display.print(ch);
        display.display();

        len++;
        if (len > 22 * 7) {
          len = 0;
          display.clearDisplay();
          display.setCursor(0, 0);
          display.display();
        }
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

void vibrate() {
  digitalWrite(VIBRATOR, LOW);
  delay(1000);
  digitalWrite(VIBRATOR, HIGH);
}

