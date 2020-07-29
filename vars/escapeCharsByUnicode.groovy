
def call(String text)
{
    def unsafeCharsRegex = /['"\\&$ <>|:\n]/

    return text.replaceAll(unsafeCharsRegex, {
        //"\\${it}"
        "\\u${Integer.toHexString(it.codePointAt(0)).padLeft(4, '0')}"
    })
}
