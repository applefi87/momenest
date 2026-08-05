# release 目前 isMinifyEnabled = false，這份規則暫時不會生效。
# 之後要上架開啟縮減時，至少需要：
#   -keepattributes *Annotation*
#   kotlinx.serialization 的 keep 規則（若改用 @Serializable 直接映射）
# Hilt / Dagger 產生的程式碼本身有內建 consumer rules，不需另外寫。
