#ifndef NEOPIXEL
#define NEOPIXEL

#include <stdint.h>
#include "stm32f4xx_hal.h"
/*
 * This struct is used to store the number of neopixels on the object we use and what color they should be
 */
typedef struct neopixel_s {
	uint32_t npixel; //number of pixels
	uint8_t* red; //red composant of each pixel
	uint8_t* green;
	uint8_t* blue;
	uint8_t* bufferSPI; //The message we send throught SPI to color the pixels
} neopixel;

void nP_create(neopixel* ret,uint32_t npixel);
void nP_setPixel(neopixel* np,uint32_t n, uint32_t rgb);

#endif
