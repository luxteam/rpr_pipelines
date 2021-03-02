def call(String path, String ver_prefix, String delimiter = '.', String format = "false")
{
    String new_prefix = ver_prefix.replace('"', '""')
    String currentversion=python3(
        "${CIS_TOOLS}/version_read.py --file \"${path}\" --prefix \"${new_prefix}\" --delimiter \"${delimiter}\" --format \"${format}\""
    ).split('\r\n')[2].trim()
    return currentversion
}
