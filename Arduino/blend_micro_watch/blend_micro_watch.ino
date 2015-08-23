#include <SPI.h>
#include <Wire.h>
#include <SFE_BMP180.h>
#include <boards.h>
#include <RBL_nRF8001.h>
#include <Adafruit_GFX.h>
#include <Adafruit_SSD1306.h>
#include "Clock.h"
#include "I2Cdev.h"
#include "MPU6050.h"

// Turn off the display if there is no notification
// #define SAVE_BATTERY

// Use gyro MPU6050
// #define GYRO

// Use baro BMP180
#define BARO

#define DISPLAY    5
#define VIBRATOR   8
#define OLED_DC    9
#define OLED_CS    10
#define OLED_CLK   11
#define OLED_MOSI  12
#define OLED_RESET 13

Adafruit_SSD1306 display(OLED_MOSI, OLED_CLK, OLED_DC, OLED_RESET, OLED_CS);
Clock clock;

#ifdef GYRO
MPU6050 accelgyro;
#endif

#ifdef BARO
SFE_BMP180 pressure;
#endif

void setup() {
  ble_begin();

#ifdef GYRO
  Wire.begin();
#endif

#ifdef BARO
  if (!pressure.begin()) {
    while(1);
  }
#endif

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

#ifdef GYRO
  accelgyro.initialize();
#endif

  Serial.println("setup ok");
}

// Show setup message for some time
long timer = 5000;
boolean new_msg = true;

// Check out ble_connected(). If state changes then vibrate.
boolean ble_state = false;

#ifdef GYRO
long gyroTimer = 0;
boolean gyroNewData = false;
int16_t ax = 0, ay = 0, az = 0;
int16_t gx = 0, gy = 0, gz = 0;
#endif

#ifdef BARO
long baroTimer = 0;
boolean baroNewData = false;
char baroStatus;
double baroTemp = 0, baroPress = 0;
#endif

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

#ifdef GYRO
  if (gyroTimer < millis()) {
    gyroTimer = millis() + 1000;
    gyroNewData = true;
    accelgyro.getMotion6(&ax, &ay, &az, &gx, &gy, &gz);
  }
#endif

#ifdef BARO
  //if (baroTimer < millis()) {
  //  baroTimer = millis() + 1000;
  //  baroNewData = true;

    baroStatus = pressure.startTemperature();
    if (baroStatus != 0) {
      delay(baroStatus);
      baroStatus = pressure.getTemperature(baroTemp);
      if (baroStatus != 0) {
        baroStatus = pressure.startPressure(3);
        if (baroStatus != 0) {
          baroStatus = pressure.getPressure(baroPress, baroTemp);
          if (baroStatus != 0) {
            /*baroAlt = pressure.altitude(
              baroPress,
              pressure.sealevel(baroPress, 0)
            );*/
          }
        }
      }
    }
  //}
#endif

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

#ifdef GYRO
    // Send gyro
    if (gyroNewData) {
      gyroNewData = false;
      ble_write('[');
      ble_write(gx);
      ble_write(',');
      ble_write(gy);
      ble_write(',');
      ble_write(gz);
      ble_write(']');
    }
#endif

    // Re-send serial input to the bluetooth. MPU-6050 generates
    // some data to serial input.
    /*while (Serial.available()) {
      ble_write(Serial.read());
    }*/
  }
/*
#ifdef BARO
  if (baroNewData) {
    baroNewData = false;
    ble_write('[');
    ble_write(baroTemp);
    ble_write(',');
    ble_write(baroPress);
    ble_write(']');
  }
#endif
*/
  boolean tmp_state = ble_connected();
  if (tmp_state != ble_state) {
    ble_state = tmp_state;
    vibrate();
  }

  ble_do_events();
}

void show_clock() {
  display.clearDisplay();

  // Show connection and voltage
  display.setCursor(0, 57);
  if (!ble_state) {
    display.print("(!) ");
  }

//  display.print(read_vcc());

//
  /*int val = analogRead(A5);
  display.print(",");
  display.print(val);*/
//

#ifdef GYRO
  /*display.print(ax); display.print(" ");
  display.print(ay); display.print(" ");
  display.print(az); display.print(" ");*/
  display.print(gx); display.print("x");
  display.print(gy); display.print("x");
  display.print(gz);
#endif

#ifdef BARO
  display.print(baroTemp);
  display.print("C ");
  display.print(baroPress);
  display.print("mb ");
#endif

  // Show clock
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

long read_vcc() {
  // Read 1.1V reference against AVcc
  // set the reference to Vcc and the measurement to the internal 1.1V reference
  #if defined(__AVR_ATmega32U4__) || defined(__AVR_ATmega1280__) || defined(__AVR_ATmega2560__)
    ADMUX = _BV(REFS0) | _BV(MUX4) | _BV(MUX3) | _BV(MUX2) | _BV(MUX1);
  #elif defined (__AVR_ATtiny24__) || defined(__AVR_ATtiny44__) || defined(__AVR_ATtiny84__)
    ADMUX = _BV(MUX5) | _BV(MUX0);
  #elif defined (__AVR_ATtiny25__) || defined(__AVR_ATtiny45__) || defined(__AVR_ATtiny85__)
    ADMUX = _BV(MUX3) | _BV(MUX2);
  #else
    ADMUX = _BV(REFS0) | _BV(MUX3) | _BV(MUX2) | _BV(MUX1);
  #endif  

  delay(2); // Wait for Vref to settle
  ADCSRA |= _BV(ADSC); // Start conversion
  while (bit_is_set(ADCSRA,ADSC)); // measuring

  uint8_t low  = ADCL; // must read ADCL first - it then locks ADCH  
  uint8_t high = ADCH; // unlocks both

  long result = (high<<8) | low;

  result = 1125300L / result; // Calculate Vcc (in mV); 1125300 = 1.1*1023*1000
  return result; // Vcc in millivolts
}

