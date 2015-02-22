#ifndef Clock_h
#define Clock_h

#include "Arduino.h"

class Clock {
  public:
    Clock();
    void proceed();
    void set(int hours, int minutes, int seconds);
    int h, m, s;
  private:
    int ms;
    long jobTime;
};

#endif

