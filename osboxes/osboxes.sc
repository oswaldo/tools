//> using scala 3.3.1
//> using file "../common/core.sc"
//> using file "../common/tools.sc"
//> using toolkit latest

import core.*
import tools.*
import util.*
import os.*

p7zip.installIfNeeded()

enum vmImage(downloadable: DownloadableFile, vdiFile: String, expectedVdiSha256: String):
  case ubuntu23_04
      extends vmImage(
        DownloadableFile(
          "Ubuntu 23.04 Lunar Lobster.7z",
          "https://downloads.sourceforge.net/project/osboxes/v/vb/55-U-u/23.04/64bit.7z?ts=gAAAAABlJQqzW7hKBaFIDgqyIWFBxHHnRs-3Q-W53ilVdrFxkEi8eVVgc900gfGs2CdHANs_32Rq62GqAEM1o1C4Dmirct3PpA%3D%3D&r=https%3A%2F%2Fsourceforge.net%2Fprojects%2Fosboxes%2Ffiles%2Fv%2Fvb%2F55-U-u%2F23.04%2F64bit.7z%2Fdownload%3Fuse_mirror%3Dautoselect",
          "8a139e38b3713e2edea2ead80ebfd80e4478cdba750c518008ce89321b2862cd",
        ),
        "Ubuntu 23.04 (64bit).vdi",
        "9073c2e58f95fba2c8f6816bafb1eead70ef635de87903fc74d9d15c2974853e",
      )
  def download() =
    val downloadPath        = os.pwd / "osboxes" / "downloads"
    val vdiFilePath         = downloadPath / vdiFile
    val downloadFilePath    = downloadPath / downloadable.name
    val extractedFolderPath = downloadPath / "64bit"
    // first check if the file is already downloaded and the sha256 matches:
    if exists(vdiFilePath) then
      if shasum.sha256sumCheck(vdiFilePath, expectedVdiSha256) then println("Already downloaded " + vdiFilePath)
      else println("Removing " + vdiFilePath)
    if !exists(vdiFilePath) then
      println("Downloading " + vdiFilePath)
      makeDir.all(downloadPath)
      curl.download(downloadable, Some(downloadFilePath))
      if !exists(extractedFolderPath) then
        println("Extracting " + downloadFilePath)
        p7zip.extract(downloadFilePath, downloadPath)
        move(extractedFolderPath / vdiFile, vdiFilePath)
      println("Downloaded " + vdiFilePath)
    // remove extractedFolderPath and downloadFilePath if they are still laying around:
    if exists(extractedFolderPath) then remove.all(extractedFolderPath)
    if exists(downloadFilePath) then remove.all(downloadFilePath)

    // TODO think about using `Using` so we hide the removal of files under the close() method
