package io.github.freevideocaster.roku

import java.io.IOException

/** Error con un mensaje pensado para mostrarse tal cual al usuario. */
class CastException(message: String) : IOException(message)
