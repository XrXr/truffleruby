require_relative '../../spec_helper'

describe "RUBY_VERSION" do
  it "is a String" do
    RUBY_VERSION.should be_kind_of(String)
  end
end

describe "RUBY_PATCHLEVEL" do
  it "is a Fixnum" do
    RUBY_PATCHLEVEL.should be_kind_of(Fixnum)
  end
end

describe "RUBY_COPYRIGHT" do
  it "is a String" do
    RUBY_COPYRIGHT.should be_kind_of(String)
  end
end

describe "RUBY_DESCRIPTION" do
  it "is a String" do
    RUBY_DESCRIPTION.should be_kind_of(String)
  end
end

describe "RUBY_ENGINE" do
  it "is a String" do
    RUBY_ENGINE.should be_kind_of(String)
  end
end

describe "RUBY_PLATFORM" do
  it "is a String" do
    RUBY_PLATFORM.should be_kind_of(String)
  end

  platform_is_not :windows do
    it 'contains the current kernel major version' do
      kernel_version = `uname -r`
      RUBY_PLATFORM.should =~ /#{kernel_version.split('.').first}/
    end
  end
end

describe "RUBY_RELEASE_DATE" do
  it "is a String" do
    RUBY_RELEASE_DATE.should be_kind_of(String)
  end
end

describe "RUBY_REVISION" do
  ruby_version_is ""..."2.7" do
    it "is an Integer" do
      RUBY_REVISION.should be_kind_of(Fixnum)
    end
  end

  ruby_version_is "2.7" do
    it "is a String" do
      RUBY_REVISION.should be_kind_of(String)
    end
  end
end
