//> using scala 3.3.1
//> using file "../common/core.sc"
//> using file "../common/tools.sc"
//> using dep "com.lihaoyi::os-lib::0.9.1"

import core.*
import tools.*
import util.*
import os.*

enum vmImage(downloadable: DownloadableFile):
  case ubuntu23_04
      extends vmImage(
        DownloadableFile(
          "Ubuntu 23.04 Lunar Lobster.7z",
          "https://downloads.sourceforge.net/project/osboxes/v/vb/55-U-u/23.04/64bit.7z?ts=gAAAAABlJQqzW7hKBaFIDgqyIWFBxHHnRs-3Q-W53ilVdrFxkEi8eVVgc900gfGs2CdHANs_32Rq62GqAEM1o1C4Dmirct3PpA%3D%3D&r=https%3A%2F%2Fsourceforge.net%2Fprojects%2Fosboxes%2Ffiles%2Fv%2Fvb%2F55-U-u%2F23.04%2F64bit.7z%2Fdownload%3Fuse_mirror%3Dautoselect",
          "8a139e38b3713e2edea2ead80ebfd80e4478cdba750c518008ce89321b2862cd",
        ),
      )
  def downloadFilePath(): Path =
    os.pwd / "osboxes" / "downloads" / downloadable.name
  def download() =
    curl.download(downloadable, Some(downloadFilePath()))
