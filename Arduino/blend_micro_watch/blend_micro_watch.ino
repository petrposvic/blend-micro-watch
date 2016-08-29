#include <RBL_nRF8001.h>
#include "Adafruit_SSD1306.h"
#include "MyClock.h"

#define VIBRATOR   8
#define OLED_RESET 4

Adafruit_SSD1306 oled(OLED_RESET);
MyClock myclock;

void setup() {
  Serial.begin(9600);

  // Bluetooth
  ble_begin();

  // Display
  oled.begin(SSD1306_SWITCHCAPVCC, 0x3C);
  oled.setTextSize(1);
  oled.setTextColor(WHITE);
  oled.clearDisplay();
  oled.display();

  pinMode(VIBRATOR, OUTPUT);
  digitalWrite(VIBRATOR, LOW); // Pullup
}

uint8_t unread_msg_count = 0;
uint32_t new_msg_timer = 0;

// Check out ble_connected(). If state changes then vibrate.
boolean ble_state = false;

void loop() {
  if (new_msg_timer < millis()) {
    myclock.proceed();
    show();
  }

  if (ble_available()) {

    while (ble_available()) {
      char ch = ble_read();

      // Show notification
      if (ch == '~') {
        Serial.println();
        vibrate();

        if (unread_msg_count < 255) {
          unread_msg_count++;
        }

        // Show clock on top
        oled.clearDisplay();
        oled.setTextSize(1);
        oled.setCursor(0, 0);
        oled.print("       ");
        if (myclock.h < 10) oled.print("0");
        oled.print(myclock.h);
        oled.print(":");
        if (myclock.m < 10) oled.print("0");
        oled.print(myclock.m);
        oled.print(":");
        if (myclock.s < 10) oled.print("0");
        oled.print(myclock.s);
        oled.println();
        oled.display();
      } else

      // Decrease unread messages count
      if (ch == '^') {
        if (unread_msg_count > 0) {
          unread_msg_count--;
        }
      } else

      // Set clock
      if (ch == '&') {
        int hms[] = {0, 0, 0};
        for (int i = 0; i < 6; i++) {
          if (!ble_available()) {
            // Serial.println("error in setting clock");
            break;
          }

          ch = ble_read();
          // Serial.print(ch);

          if (i % 2) {
            hms[i / 2] += ch - 48;
          } else {
            hms[i / 2] += (ch - 48) * 10;
          }
        }

        myclock.h = hms[0];
        myclock.m = hms[1];
        myclock.s = hms[2];
      } else

      // Print message char by char
      {
        Serial.write(ch);

        // Each char prolong display timer
        new_msg_timer = millis() + 1000 * 5;

        oled.print(ch);
        oled.display();
      }
    }

    Serial.println();
  }

  boolean tmp_state = ble_connected();
  if (tmp_state != ble_state) {
    ble_state = tmp_state;
    vibrate();
  }

  ble_do_events();
}

void show() {
  oled.clearDisplay();

  // Show unread new messages and BLE connection
  oled.setCursor(0, oled.height() - 8);
  oled.setTextSize(1);
  if (unread_msg_count > 0) {
    oled.print("[");
    oled.print(unread_msg_count);
    oled.print("]");
  }
  if (!ble_state) {
    oled.print("(!) ");
  }
  oled.print(read_vcc());

  // Show big clock
  oled.setCursor(0, 0);
  oled.setTextSize(4);
  if (myclock.h < 10) oled.print("0");
  oled.print(myclock.h);
  oled.print(":");
  if (myclock.m < 10) oled.print("0");
  oled.print(myclock.m);
  oled.print("  :");
  if (myclock.s < 10) oled.print("0");
  oled.print(myclock.s);

  oled.display();
}

void vibrate() {
  digitalWrite(VIBRATOR, HIGH);
  delay(1000);
  digitalWrite(VIBRATOR, LOW);
}

long read_vcc() {
  ADMUX = _BV(REFS0) | _BV(MUX4) | _BV(MUX3) | _BV(MUX2) | _BV(MUX1);

  delay(2);
  ADCSRA |= _BV(ADSC);
  while (bit_is_set(ADCSRA, ADSC));

  uint8_t low  = ADCL;
  uint8_t high = ADCH;

  long result = (high<<8) | low;

  result = 1125300L / result;

  // Vcc in millivolts
  return result;
}

