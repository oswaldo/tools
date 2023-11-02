//> using scala 3.3.1
//> using file "../common/core.sc"
//> using file "../common/tools.sc"
//> using toolkit latest

import core.*
import tools.*
import util.*
import os.*
import java.util.UUID

//This is an interesting case as transformers is not actually a tool, but a python library

case class TransformersModel(projectName: String, modelName: String, padToken: String, revisions: List[String]):
  val id: String = s"$projectName/$modelName"

val knownModels: Map[String, TransformersModel] =
  List(
    TransformersModel(
      projectName = "EleutherAI",
      modelName = "gpt-neo-125M",
      padToken = "<|endoftext|>",
      revisions = "main" :: Nil,
    ),
  )
    .map(m => m.id -> m)
    .toMap

given transformerModelParser: (String => TransformersModel) = m =>
  Try(knownModels(m)) match
    case Success(model) => model
    case _              => throw new Exception(s"Unknown model: $m. Try adding it to the knownModels map in transformers.sc")

case class GenerationArgs(
  val input: String,
  val model: TransformersModel,
  val modelRevision: String,
  val maxLength: Int,
):
  require(maxLength > 0, s"maxLength must be greater than 0 (got $maxLength))")
  require(input.trim().length > 0, s"input must be non-empty (got $input))")
  require(
    model.revisions.contains(modelRevision),
    s"modelRevision must be one of ${model.revisions.mkString(", ")} (got $modelRevision))",
  )

object transformers extends Tool("transformers", RequiredVersion.any(python)):
  override def installedVersion()(using wd: MaybeGiven[Path]): InstalledVersion =
    python.installedPackageVersion(name)

  def generate(args: GenerationArgs): String =
    import args.*
    python.executeVerboseText(
      os.pwd / "transformers" / "transformersModelTrigger.py",
      "--model_name",
      model.id,
      "--revision",
      modelRevision,
      "--cache_dir",
      (InstallFolder / "transformersCache").toString,
      "--pad_token",
      model.padToken,
      "--max_length",
      maxLength.toString,
      "--input_text",
      input,
    )
